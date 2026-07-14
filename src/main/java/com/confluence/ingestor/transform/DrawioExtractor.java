package com.confluence.ingestor.transform;

import com.confluence.ingestor.attachment.AttachmentType;
import com.confluence.ingestor.attachment.DiagramEdge;
import com.confluence.ingestor.attachment.DiagramModel;
import com.confluence.ingestor.attachment.DiagramNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extracts text labels from draw.io {@code mxfile} / {@code mxGraphModel} XML.
 */
@Component
public class DrawioExtractor {

    private static final Pattern DRAWIO_MARKER =
            Pattern.compile("<mx(?:file|GraphModel)\\b", Pattern.CASE_INSENSITIVE);

    public boolean looksLikeDrawioXml(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return DRAWIO_MARKER.matcher(content).find();
    }

    public List<String> extractLabels(String drawioXml) {
        return extractModel(drawioXml, null, null).labels();
    }

    public DiagramModel extractModel(String drawioXml, String attachmentId, String fileName) {
        if (!looksLikeDrawioXml(drawioXml)) {
            return new DiagramModel(attachmentId, null, AttachmentType.GENERIC_DIAGRAM, List.of(), List.of(), List.of());
        }

        Document document = Jsoup.parse(drawioXml, "", Parser.xmlParser());
        List<Element> diagrams = new ArrayList<>(document.select("diagram"));
        if (diagrams.isEmpty()) {
            diagrams = List.of(document);
        }

        DiagramModel selected = null;
        int selectedScore = Integer.MIN_VALUE;
        for (Element diagram : diagrams) {
            DiagramModel candidate = extractDiagramModel(diagram, attachmentId);
            int score = scoreForFile(candidate, fileName);
            if (selected == null || score > selectedScore) {
                selected = candidate;
                selectedScore = score;
            }
        }
        return selected != null
                ? selected
                : new DiagramModel(attachmentId, null, AttachmentType.GENERIC_DIAGRAM, List.of(), List.of(), List.of());
    }

    private DiagramModel extractDiagramModel(Element diagram, String attachmentId) {
        Set<String> labels = new LinkedHashSet<>();
        Map<String, DiagramCell> cells = new LinkedHashMap<>();
        Map<String, List<DiagramCell>> childrenByParent = new HashMap<>();
        int order = 0;
        for (Element cell : diagram.select("mxCell")) {
            DiagramCell parsed = DiagramCell.from(cell, order++);
            cells.put(parsed.id(), parsed);
            if (parsed.parentId() != null) {
                childrenByParent.computeIfAbsent(parsed.parentId(), ignored -> new ArrayList<>()).add(parsed);
            }
            String text = parsed.value();
            if (!text.isBlank()) {
                labels.add(text);
            }
        }
        childrenByParent.values().forEach(children -> children.sort(Comparator.comparingInt(DiagramCell::order)));

        AttachmentType diagramType = classifyRaw(cells.values().stream().toList(), new ArrayList<>(labels));
        List<DiagramNode> nodes = semanticNodes(diagramType, cells, childrenByParent);
        List<DiagramEdge> edges = semanticEdges(diagramType, cells, childrenByParent, nodes);
        List<String> orderedLabels = new ArrayList<>(labels);
        return new DiagramModel(
                attachmentId,
                blankToNull(diagram.attr("name")),
                diagramType,
                List.copyOf(nodes),
                List.copyOf(edges),
                List.copyOf(orderedLabels));
    }

    public String placeholder(ExtractedDiagram diagram) {
        return "[DIAGRAM:%s](%s%s)"
                .formatted(diagram.diagramId(), StorageDrawioReferenceExtractor.DIAGRAMS_ASSET_PREFIX, diagram.jsonFileName());
    }

    private static AttachmentType classifyRaw(List<DiagramCell> cells, List<String> labels) {
        String text = String.join(" ", labels).toLowerCase(Locale.ROOT);
        long directedEdges = cells.stream()
                .filter(cell -> cell.edge() && cell.sourceId() != null && cell.targetId() != null)
                .count();
        long stateTerms = labels.stream()
                .map(label -> label.toUpperCase(Locale.ROOT))
                .filter(label -> label.matches(".*\\b(WAIT|APPROVED|REJECTED|IN_PROGRESS|REQ_|RE_APPROVAL|START|RESTART)\\b.*"))
                .count();
        long transitionTerms = labels.stream()
                .map(label -> label.toLowerCase(Locale.ROOT))
                .filter(label -> label.contains("approve") || label.contains("approval") || label.contains("reject")
                        || label.contains("restart") || label.contains("review") || label.contains("provided"))
                .count();
        if (stateTerms >= 2 && (directedEdges >= 1 || transitionTerms >= 1)) {
            return AttachmentType.STATE_DIAGRAM;
        }

        long classTerms = labels.stream()
                .filter(label -> label.contains("()")
                        || label.matches(".*\\b(Long|String|Date|Collection|Integer|Boolean)\\s+\\w+.*")
                        || label.matches(".*\\b[A-Z][A-Za-z0-9]*(New|Flow|Operation|Service|Mapper|Repo)\\b.*"))
                .count();
        if (classTerms >= 3 || text.contains("class diagram")) {
            return AttachmentType.CLASS_DIAGRAM;
        }

        long erTerms = labels.stream()
                .map(label -> label.toLowerCase(Locale.ROOT))
                .filter(label -> label.contains(" pk") || label.contains(" fk")
                        || label.contains("primary key") || label.contains("foreign key"))
                .count();
        if (erTerms >= 2) {
            return AttachmentType.ER_DIAGRAM;
        }

        long architectureTerms = labels.stream()
                .map(label -> label.toLowerCase(Locale.ROOT))
                .filter(label -> label.contains("controller") || label.contains("service")
                        || label.contains("repository") || label.contains("postgres")
                        || label.contains("rabbit") || label.contains("gateway")
                        || label.contains("client") || label.contains("infrastructure")
                        || label.contains("layer") || label.contains("keycloak"))
                .count();
        if (architectureTerms >= 4) {
            return AttachmentType.ARCHITECTURE_DIAGRAM;
        }

        return AttachmentType.GENERIC_DIAGRAM;
    }

    private static List<DiagramNode> semanticNodes(
            AttachmentType diagramType,
            Map<String, DiagramCell> cells,
            Map<String, List<DiagramCell>> childrenByParent) {
        return switch (diagramType) {
            case CLASS_DIAGRAM -> classNodes(cells, childrenByParent);
            case ER_DIAGRAM -> erNodes(cells, childrenByParent);
            case STATE_DIAGRAM -> stateNodes(cells);
            case ARCHITECTURE_DIAGRAM -> architectureNodes(cells);
            default -> genericNodes(cells);
        };
    }

    private static List<DiagramNode> classNodes(
            Map<String, DiagramCell> cells,
            Map<String, List<DiagramCell>> childrenByParent) {
        List<DiagramNode> nodes = new ArrayList<>();
        Set<String> emitted = new LinkedHashSet<>();
        for (DiagramCell cell : orderedCells(cells)) {
            if (!cell.vertex() || !isClassContainer(cell, childrenByParent)) {
                continue;
            }
            String className = className(cell, childrenByParent);
            if (className == null || !emitted.add(className)) {
                continue;
            }
            ClassCompartments compartments = classCompartments(cell, childrenByParent);
            nodes.add(new DiagramNode(
                    className,
                    className,
                    "class",
                    null,
                    cell.order(),
                    compartments.attributes(),
                    compartments.methods(),
                    compartments.stereotypes()));
        }
        return nodes;
    }

    private static List<DiagramNode> stateNodes(Map<String, DiagramCell> cells) {
        List<DiagramNode> nodes = new ArrayList<>();
        Set<String> emitted = new LinkedHashSet<>();
        EdgeCounts edgeCounts = EdgeCounts.from(cells);
        for (DiagramCell cell : orderedCells(cells)) {
            if (!cell.vertex()) {
                continue;
            }
            String label = stateLabel(cell, edgeCounts);
            if (label == null || !emitted.add(label)) {
                continue;
            }
            nodes.add(new DiagramNode(label, label, stateType(label), null, cell.order()));
        }
        return nodes;
    }

    private static List<DiagramNode> erNodes(
            Map<String, DiagramCell> cells,
            Map<String, List<DiagramCell>> childrenByParent) {
        List<DiagramNode> nodes = new ArrayList<>();
        Set<String> emitted = new LinkedHashSet<>();
        for (DiagramCell cell : orderedCells(cells)) {
            if (!cell.vertex() || !isMeaningfulLabel(cell.value()) || isMultiplicity(cell.value())) {
                continue;
            }
            List<String> columns = new ArrayList<>(childrenByParent.getOrDefault(cell.id(), List.of()).stream()
                    .map(DiagramCell::value)
                    .filter(DrawioExtractor::isMeaningfulLabel)
                    .filter(label -> !isMultiplicity(label))
                    .toList());
            collectErColumns(cell.value(), columns);
            if (isLikelyColumnOnly(cell.value()) && columns.isEmpty()) {
                continue;
            }
            String table = firstLine(cell.value());
            if (table == null || !emitted.add(table)) {
                continue;
            }
            nodes.add(new DiagramNode(
                    table,
                    table,
                    "table",
                    null,
                    cell.order(),
                    deduplicate(columns),
                    List.of(),
                    List.of()));
        }
        return nodes;
    }

    private static List<DiagramNode> architectureNodes(Map<String, DiagramCell> cells) {
        List<DiagramNode> nodes = new ArrayList<>();
        Set<String> emitted = new LinkedHashSet<>();
        for (DiagramCell cell : orderedCells(cells)) {
            if (!cell.vertex() || !isMeaningfulLabel(cell.value()) || isMultiplicity(cell.value())) {
                continue;
            }
            String label = cell.value();
            if (emitted.add(label)) {
                nodes.add(new DiagramNode(label, label, architectureType(label, cell.style()), null, cell.order()));
            }
        }
        return nodes;
    }

    private static List<DiagramNode> genericNodes(Map<String, DiagramCell> cells) {
        List<DiagramNode> nodes = new ArrayList<>();
        Set<String> emitted = new LinkedHashSet<>();
        for (DiagramCell cell : orderedCells(cells)) {
            if ((!cell.vertex() && cell.value().isBlank()) || !isMeaningfulLabel(cell.value())) {
                continue;
            }
            if (cell.edge() || isMultiplicity(cell.value())) {
                continue;
            }
            String label = cell.value();
            if (emitted.add(label)) {
                nodes.add(new DiagramNode(label, label, styleType(cell), null, cell.order()));
            }
        }
        return nodes;
    }

    private static List<DiagramEdge> semanticEdges(
            AttachmentType diagramType,
            Map<String, DiagramCell> cells,
            Map<String, List<DiagramCell>> childrenByParent,
            List<DiagramNode> nodes) {
        Set<String> semanticNodes = nodes.stream()
                .map(DiagramNode::label)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<DiagramEdge> edges = new ArrayList<>();
        Set<String> emitted = new LinkedHashSet<>();
        EdgeCounts edgeCounts = EdgeCounts.from(cells);
        for (DiagramCell edge : orderedCells(cells)) {
            if (!edge.edge()) {
                continue;
            }
            String source = resolveSemanticNode(edge.sourceId(), diagramType, cells, edgeCounts, semanticNodes);
            String target = resolveSemanticNode(edge.targetId(), diagramType, cells, edgeCounts, semanticNodes);
            if (source == null || target == null || source.equals(target)) {
                continue;
            }
            EdgeSemantics semantics = edgeSemantics(edge, diagramType, childrenByParent);
            String key = source + "\u0000" + semantics.relationshipType() + "\u0000"
                    + target + "\u0000" + semantics.label();
            if (!emitted.add(key)) {
                continue;
            }
            edges.add(new DiagramEdge(
                    source,
                    target,
                    blankToNull(semantics.label()),
                    null,
                    edge.order(),
                    blankToNull(semantics.relationshipType()),
                    blankToNull(semantics.sourceMultiplicity()),
                    blankToNull(semantics.targetMultiplicity())));
        }
        return edges;
    }

    private static String resolveSemanticNode(
            String cellId,
            AttachmentType diagramType,
            Map<String, DiagramCell> cells,
            EdgeCounts edgeCounts,
            Set<String> allowedLabels) {
        DiagramCell cell = cells.get(cellId);
        while (cell != null) {
            String label = switch (diagramType) {
                case STATE_DIAGRAM -> stateLabel(cell, edgeCounts);
                case CLASS_DIAGRAM -> classEndpointLabel(cell, cells, allowedLabels);
                default -> isMeaningfulLabel(cell.value()) ? cell.value() : null;
            };
            if (label != null && (allowedLabels.isEmpty() || allowedLabels.contains(label))) {
                return label;
            }
            cell = cells.get(cell.parentId());
        }
        return null;
    }

    private static String classEndpointLabel(
            DiagramCell cell,
            Map<String, DiagramCell> cells,
            Set<String> allowedLabels) {
        if (isMeaningfulLabel(cell.value()) && allowedLabels.contains(cell.value())) {
            return cell.value();
        }
        DiagramCell parent = cells.get(cell.parentId());
        while (parent != null) {
            if (isMeaningfulLabel(parent.value()) && allowedLabels.contains(parent.value())) {
                return parent.value();
            }
            parent = cells.get(parent.parentId());
        }
        return null;
    }

    private static boolean isClassContainer(DiagramCell cell, Map<String, List<DiagramCell>> childrenByParent) {
        if (!isMeaningfulLabel(cell.value()) || isClassMember(cell.value()) || isMultiplicity(cell.value())) {
            return false;
        }
        String style = cell.style().toLowerCase(Locale.ROOT);
        if (style.contains("swimlane") || style.contains("uml") || style.contains("class")) {
            return true;
        }
        List<DiagramCell> children = childrenByParent.getOrDefault(cell.id(), List.of());
        return children.stream().anyMatch(child -> isClassMember(child.value()))
                || cell.value().matches("[A-Z][A-Za-z0-9_]*(?:New|Flow|Operation|Service|Mapper|Repository|Controller)?");
    }

    private static String className(DiagramCell cell, Map<String, List<DiagramCell>> childrenByParent) {
        if (isClassMember(cell.value())) {
            return null;
        }
        String label = cell.value();
        if (label.contains("\n")) {
            for (String line : label.split("\\R")) {
                String stripped = line.strip();
                if (isMeaningfulLabel(stripped) && !isClassMember(stripped) && !isMultiplicity(stripped)) {
                    return stripped;
                }
            }
        }
        if (isMeaningfulLabel(label)) {
            return label;
        }
        List<DiagramCell> children = childrenByParent.getOrDefault(cell.id(), List.of());
        for (DiagramCell child : children) {
            String childLabel = child.value();
            if (isMeaningfulLabel(childLabel) && !isClassMember(childLabel) && !isMultiplicity(childLabel)) {
                return childLabel;
            }
        }
        return null;
    }

    private static ClassCompartments classCompartments(
            DiagramCell cell,
            Map<String, List<DiagramCell>> childrenByParent) {
        List<String> attributes = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        List<String> stereotypes = new ArrayList<>();
        collectClassMembers(cell.value(), attributes, methods, stereotypes);
        for (DiagramCell child : childrenByParent.getOrDefault(cell.id(), List.of())) {
            collectClassMembers(child.value(), attributes, methods, stereotypes);
        }
        return new ClassCompartments(deduplicate(attributes), deduplicate(methods), deduplicate(stereotypes));
    }

    private static void collectClassMembers(
            String value,
            List<String> attributes,
            List<String> methods,
            List<String> stereotypes) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String rawLine : value.split("\\R|\\s{2,}")) {
            String line = rawLine.strip();
            if (!isMeaningfulLabel(line) || isMultiplicity(line)) {
                continue;
            }
            if (line.startsWith("<<") && line.endsWith(">>")) {
                stereotypes.add(line);
            } else if (line.contains("(") && line.contains(")")) {
                methods.add(line);
            } else if (isAttribute(line)) {
                attributes.add(line);
            }
        }
    }

    private static String stateLabel(DiagramCell cell, EdgeCounts edgeCounts) {
        if (isMeaningfulLabel(cell.value())) {
            return cell.value();
        }
        if (isStartPseudoNode(cell, edgeCounts)) {
            return "START";
        }
        if (isEndPseudoNode(cell, edgeCounts)) {
            return "END";
        }
        return null;
    }

    private static boolean isStartPseudoNode(DiagramCell cell, EdgeCounts edgeCounts) {
        String style = cell.style().toLowerCase(Locale.ROOT);
        return cell.vertex()
                && style.contains("ellipse")
                && (style.contains("fillcolor=#000000") || style.contains("fillcolor=black")
                || style.contains("fillcolor=#000") || style.contains("strokeColor=none".toLowerCase(Locale.ROOT)))
                && edgeCounts.outgoing(cell.id()) > 0
                && edgeCounts.incoming(cell.id()) == 0;
    }

    private static boolean isEndPseudoNode(DiagramCell cell, EdgeCounts edgeCounts) {
        String style = cell.style().toLowerCase(Locale.ROOT);
        return cell.vertex()
                && (style.contains("doubleellipse") || style.contains("shape=endstate")
                || style.contains("shape=umlfinalstate") || style.contains("shape=mxgraph.uml25.final_state")
                || (style.contains("ellipse") && style.contains("fillcolor=#000000") && edgeCounts.incoming(cell.id()) > 0))
                && edgeCounts.incoming(cell.id()) > 0;
    }

    private static String stateType(String label) {
        return switch (label) {
            case "START" -> "state-start";
            case "END" -> "state-end";
            default -> "state";
        };
    }

    private static EdgeSemantics edgeSemantics(
            DiagramCell edge,
            AttachmentType diagramType,
            Map<String, List<DiagramCell>> childrenByParent) {
        if (diagramType == AttachmentType.CLASS_DIAGRAM) {
            String type = classRelationshipType(edge.style());
            Multiplicity multiplicity = multiplicity(edgeLabels(edge, childrenByParent));
            String label = relationshipLabel(type, multiplicity);
            return new EdgeSemantics(label, type, multiplicity.source(), multiplicity.target());
        }
        if (diagramType == AttachmentType.ER_DIAGRAM) {
            String label = edgeLabels(edge, childrenByParent);
            return new EdgeSemantics(label.isBlank() ? "references" : label, "reference", null, null);
        }
        return new EdgeSemantics(edge.value(), null, null, null);
    }

    private static String relationshipLabel(String type, Multiplicity multiplicity) {
        if (!multiplicity.summary().isBlank() && "association".equals(type)) {
            return multiplicity.summary() + " association";
        }
        if (!multiplicity.summary().isBlank()) {
            return type.isBlank() ? multiplicity.summary() : type + " " + multiplicity.summary();
        }
        return type;
    }

    private static String edgeLabels(DiagramCell edge, Map<String, List<DiagramCell>> childrenByParent) {
        List<String> labels = new ArrayList<>();
        if (isMeaningfulLabel(edge.value())) {
            labels.add(edge.value());
        }
        for (DiagramCell child : childrenByParent.getOrDefault(edge.id(), List.of())) {
            if (isMeaningfulLabel(child.value())) {
                labels.add(child.value());
            }
        }
        return String.join(" ", deduplicate(labels)).strip();
    }

    private static String classRelationshipType(String style) {
        String lower = style.toLowerCase(Locale.ROOT);
        if (lower.contains("startarrow=block") || lower.contains("endarrow=block")
                || lower.contains("startarrow=blockthin") || lower.contains("endarrow=blockthin")) {
            return lower.contains("dashed=1") ? "implements" : "extends";
        }
        if (lower.contains("diamond=1") || lower.contains("startarrow=diamond")
                || lower.contains("endarrow=diamond")) {
            return "aggregation";
        }
        if (lower.contains("diamond=thin") || lower.contains("startarrow=diamondthin")
                || lower.contains("endarrow=diamondthin")) {
            return "composition";
        }
        if (lower.contains("dashed=1")) {
            return "dependency";
        }
        return "association";
    }

    private static Multiplicity multiplicity(String label) {
        if (label == null || label.isBlank()) {
            return new Multiplicity("", "", "");
        }
        String normalized = label.strip();
        if (normalized.matches("(?i).*\\b1\\b.*(?:1\\.\\.\\*|0\\.\\.\\*|\\*|many).*")
                || normalized.matches("(?i).*(?:1\\.\\.\\*|0\\.\\.\\*|\\*|many).*\\b1\\b.*")) {
            return new Multiplicity("one-to-many", "1", "many");
        }
        if (normalized.matches("(?i)(1|0\\.\\.1|1\\.\\.\\*|0\\.\\.\\*|\\*|many|one)(\\s*(to|:|-|\\.\\.)\\s*(1|0\\.\\.1|1\\.\\.\\*|0\\.\\.\\*|\\*|many|one))?")) {
            String summary = normalized
                    .replace("1..*", "one-to-many")
                    .replace("0..*", "zero-to-many")
                    .replace("*", "many");
            return new Multiplicity(summary, "", "");
        }
        return new Multiplicity("", "", "");
    }

    private static boolean isClassMember(String label) {
        return isAttribute(label) || (label != null && label.contains("(") && label.contains(")"));
    }

    private static boolean isAttribute(String label) {
        if (label == null) {
            return false;
        }
        String stripped = label.strip();
        return stripped.matches("[-+#~]?\\s*(?:[A-Z][A-Za-z0-9_<>?, ]+\\s+)?[a-z][A-Za-z0-9_]*\\s*:\\s*[A-Z][A-Za-z0-9_<>?, ]+")
                || stripped.matches("[-+#~]?\\s*(?:[A-Z][A-Za-z0-9_<>?, ]*|Collection<[^>]+>|List<[^>]+>|Set<[^>]+>|Map<[^>]+>)\\s+[a-z][A-Za-z0-9_]*");
    }

    private static boolean isMultiplicity(String label) {
        return label != null && label.strip().matches("(?:0\\.\\.1|0\\.\\.\\*|1\\.\\.\\*|1|\\*|many|one)");
    }

    private static boolean isMeaningfulLabel(String label) {
        return label != null && !label.isBlank() && !label.matches("[0-9a-fA-F-]{6,}");
    }

    private static String architectureType(String label, String style) {
        String lower = label.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "web frontend", "order service", "other microservice")) {
            return "inbound";
        }
        if (lower.contains("controller")) {
            return "controller-layer";
        }
        if (containsAny(lower, "approverflowservice", "approve reject", "approvereject", "emailnotification")) {
            return "core-business-service";
        }
        if (containsAny(lower, "techmanager", "additional", "historyservice", "replication")) {
            return "supporting-service";
        }
        if (containsAny(lower, "security", "userservice", "commonservice", "auditlogging")) {
            return "cross-cutting-service";
        }
        if (containsAny(lower, "approval flow", "business rule", "approver calculation", "validation logic")) {
            return "domain-logic";
        }
        if (lower.contains("mapper")) {
            return "data-mapping";
        }
        if (lower.contains("repository") || lower.contains("repo")) {
            return "data-access";
        }
        if (lower.contains("postgres") || lower.contains("database") || lower.contains("db")) {
            return "database";
        }
        if (containsAny(lower, "approval data", "audit history", "envers")) {
            return "database";
        }
        if (containsAny(lower, "rabbit", "kafka", "queue", "topic", "hibernate", "cache", "configuration", "transaction", "infrastructure")) {
            return "infrastructure";
        }
        if (lower.contains("client") || lower.contains("keycloak") || lower.contains("gateway")) {
            return "external-client";
        }
        if (lower.contains("service")) {
            return "supporting-service";
        }
        if (lower.contains("layer")) {
            return "layer";
        }
        return styleTypeFromString(style);
    }

    private static List<DiagramCell> orderedCells(Map<String, DiagramCell> cells) {
        return cells.values().stream().sorted(Comparator.comparingInt(DiagramCell::order)).toList();
    }

    private static List<String> deduplicate(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private static void collectErColumns(String value, List<String> columns) {
        if (value == null) {
            return;
        }
        String[] lines = value.split("\\R");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].strip();
            if (isMeaningfulLabel(line)) {
                columns.add(line);
            }
        }
    }

    private static String firstLine(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.split("\\R", 2)[0].strip();
    }

    private static boolean isLikelyColumnOnly(String label) {
        if (label == null) {
            return false;
        }
        String lower = label.toLowerCase(Locale.ROOT);
        return lower.contains(" pk") || lower.contains(" fk") || lower.contains("primary key")
                || lower.contains("foreign key") || lower.matches(".*\\b(id|uuid|created_at|updated_at)\\b.*");
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static int scoreForFile(DiagramModel model, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return 0;
        }
        String normalizedFile = fileName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ");
        String diagramName = model.diagramName() != null ? model.diagramName().toLowerCase(Locale.ROOT) : "";
        String labels = String.join(" ", model.labels()).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : normalizedFile.split("\\s+")) {
            if (token.length() < 3 || "drawio".equals(token) || "png".equals(token)) {
                continue;
            }
            if (diagramName.contains(token)) {
                score += 12;
            }
            if (labels.contains(token)) {
                score += 2;
            }
        }
        if (normalizedFile.contains("state") && model.diagramType() == AttachmentType.STATE_DIAGRAM) {
            score += 100;
        }
        if (normalizedFile.contains("class") && model.diagramType() == AttachmentType.CLASS_DIAGRAM) {
            score += 100;
        }
        if ((normalizedFile.contains("arch") || normalizedFile.contains("architecture"))
                && model.diagramType() == AttachmentType.ARCHITECTURE_DIAGRAM) {
            score += 100;
        }
        return score;
    }

    private static String styleType(DiagramCell cell) {
        return styleTypeFromString(cell.style());
    }

    private static String styleTypeFromString(String style) {
        if (style == null || style.isBlank()) {
            return null;
        }
        for (String part : style.split(";")) {
            if (part.startsWith("shape=") || part.startsWith("swimlane") || part.startsWith("rounded=")) {
                return part;
            }
        }
        return null;
    }

    private static String sanitizeLabel(String rawValue) {
        String decoded = rawValue
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        if (decoded.contains("<")) {
            return Jsoup.parse(decoded).text().replace('\n', ' ').strip();
        }
        return decoded.replace('\n', ' ').strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private record DiagramCell(
            String id,
            String parentId,
            String value,
            boolean vertex,
            boolean edge,
            String sourceId,
            String targetId,
            String style,
            Geometry geometry,
            int order) {

        static DiagramCell from(Element cell, int order) {
            Element geometry = cell.selectFirst("mxGeometry");
            return new DiagramCell(
                    blankToNull(cell.attr("id")),
                    blankToNull(cell.attr("parent")),
                    sanitizeLabel(cell.attr("value")),
                    "1".equals(cell.attr("vertex")),
                    "1".equals(cell.attr("edge")),
                    blankToNull(cell.attr("source")),
                    blankToNull(cell.attr("target")),
                    cell.attr("style"),
                    Geometry.from(geometry),
                    order);
        }
    }

    private record Geometry(String x, String y, String width, String height) {
        static Geometry from(Element element) {
            if (element == null) {
                return new Geometry(null, null, null, null);
            }
            return new Geometry(
                    blankToNull(element.attr("x")),
                    blankToNull(element.attr("y")),
                    blankToNull(element.attr("width")),
                    blankToNull(element.attr("height")));
        }
    }

    private record ClassCompartments(List<String> attributes, List<String> methods, List<String> stereotypes) {
    }

    private record EdgeSemantics(
            String label,
            String relationshipType,
            String sourceMultiplicity,
            String targetMultiplicity) {
    }

    private record Multiplicity(String summary, String source, String target) {
    }

    private record EdgeCounts(Map<String, Integer> incoming, Map<String, Integer> outgoing) {
        static EdgeCounts from(Map<String, DiagramCell> cells) {
            Map<String, Integer> incoming = new HashMap<>();
            Map<String, Integer> outgoing = new HashMap<>();
            for (DiagramCell cell : cells.values()) {
                if (!cell.edge()) {
                    continue;
                }
                if (cell.sourceId() != null) {
                    outgoing.merge(cell.sourceId(), 1, Integer::sum);
                }
                if (cell.targetId() != null) {
                    incoming.merge(cell.targetId(), 1, Integer::sum);
                }
            }
            return new EdgeCounts(incoming, outgoing);
        }

        int incoming(String id) {
            return id == null ? 0 : incoming.getOrDefault(id, 0);
        }

        int outgoing(String id) {
            return id == null ? 0 : outgoing.getOrDefault(id, 0);
        }
    }
}
