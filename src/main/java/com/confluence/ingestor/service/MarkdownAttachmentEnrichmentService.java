package com.confluence.ingestor.service;

import com.confluence.ingestor.attachment.AttachmentType;
import com.confluence.ingestor.model.AttachmentManifestEntry;
import com.confluence.ingestor.model.AttachmentsManifestDocument;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Injects already extracted attachment information into the canonical Markdown document.
 */
@Service
public class MarkdownAttachmentEnrichmentService {

    public static final String ENRICHMENT_HEADING = "### Extracted Attachment Information";
    private static final int MAX_RAW_LABELS = 200;

    private static final Pattern IMAGE_REF = Pattern.compile("!\\[[^\\]]*]\\(([^)]+)\\)");
    private static final Pattern RAW_DRAWIO_ID = Pattern.compile("(?i)(?:mxcell[-_ ]*)?[a-z]{0,3}\\d{1,6}|[0-9a-f]{8}-[0-9a-f-]{13,}");

    public String enrich(String markdown, AttachmentsManifestDocument manifest) {
        if (markdown == null || markdown.isBlank() || manifest == null || manifest.getAttachments() == null) {
            return markdown;
        }

        Map<String, AttachmentManifestEntry> byPath = indexByPath(manifest.getAttachments());
        if (byPath.isEmpty()) {
            return markdown;
        }

        StringBuilder enriched = new StringBuilder();
        String[] lines = markdown.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            enriched.append(line);
            List<AttachmentManifestEntry> referenced = findReferencedAttachments(line, byPath);
            for (AttachmentManifestEntry attachment : referenced) {
                String info = renderAttachmentInformation(attachment);
                if (!info.isBlank()) {
                    enriched.append("\n\n").append(info);
                }
            }
            if (i < lines.length - 1) {
                enriched.append('\n');
            }
        }
        return enriched.toString().strip();
    }

    private static Map<String, AttachmentManifestEntry> indexByPath(List<AttachmentManifestEntry> attachments) {
        Map<String, AttachmentManifestEntry> byPath = new LinkedHashMap<>();
        for (AttachmentManifestEntry attachment : attachments) {
            if (attachment.getRelativePath() != null && !attachment.getRelativePath().isBlank()) {
                byPath.put(normalizePath(attachment.getRelativePath()), attachment);
            }
            if (attachment.getFileName() != null && !attachment.getFileName().isBlank()) {
                byPath.putIfAbsent(normalizePath(attachment.getFileName()), attachment);
            }
        }
        return byPath;
    }

    private static List<AttachmentManifestEntry> findReferencedAttachments(
            String line,
            Map<String, AttachmentManifestEntry> byPath) {
        List<AttachmentManifestEntry> referenced = new ArrayList<>();
        Matcher matcher = IMAGE_REF.matcher(line);
        while (matcher.find()) {
            String path = normalizePath(matcher.group(1));
            AttachmentManifestEntry attachment = byPath.get(path);
            if (attachment == null && path.startsWith("assets/")) {
                attachment = byPath.get(path.substring("assets/".length()));
            }
            if (attachment != null && !referenced.contains(attachment)) {
                referenced.add(attachment);
            }
        }
        return referenced;
    }

    private static String renderAttachmentInformation(AttachmentManifestEntry attachment) {
        StringBuilder builder = new StringBuilder(ENRICHMENT_HEADING).append("\n\n");
        appendLine(builder, "File", attachment.getFileName());
        appendLine(builder, "Attachment Type", displayType(attachment.getDetectedType()));

        String searchableSummary = attachment.getSearchableSummary();
        Map<String, Object> metadata = attachment.getExtractedMetadata();
        if (hasSearchableContent(searchableSummary, metadata, attachment.getDetectedType())) {
            if (isDiagram(attachment, metadata)) {
                appendDiagramSections(builder, attachment, searchableSummary, metadata);
            } else {
                appendSection(builder, "Summary", searchableSummary);
                appendMetadataSections(builder, metadata);
            }
        } else if (isLikelyErdImage(attachment)) {
            builder.append("Data Model Summary\n\n");
            builder.append("Structured ERD extraction is unavailable because no deterministic diagram metadata was found.\n\n");
        } else {
            builder.append("Searchable Content:\n\n");
            builder.append("No visual content extracted because image analysis is disabled or unavailable.\n\n");
        }

        builder.append("---");
        return builder.toString().strip();
    }

    private static void appendDiagramSections(
            StringBuilder builder,
            AttachmentManifestEntry attachment,
            String searchableSummary,
            Map<String, Object> metadata) {
        AttachmentType diagramType = diagramType(attachment, metadata);
        if (!isValidDiagramMetadata(diagramType, metadata)) {
            appendSection(builder, "Diagram Summary", conciseDiagramSummary(diagramType, metadata));
            appendSection(builder, "Key Labels", deduplicatedStringList(metadata != null ? metadata.get("labels") : null, 40));
            appendRawLabelsAppendix(builder, metadata);
            return;
        }
        switch (diagramType) {
            case STATE_DIAGRAM -> appendStateDiagram(builder, searchableSummary, metadata);
            case CLASS_DIAGRAM -> appendClassDiagram(builder, searchableSummary, metadata);
            case ARCHITECTURE_DIAGRAM -> appendArchitectureDiagram(builder, searchableSummary, metadata);
            case ER_DIAGRAM -> appendErDiagram(builder, searchableSummary, metadata);
            default -> {
                appendSection(builder, "Diagram Summary", searchableSummary);
                appendSection(builder, "Key Labels", limitedList(metadata != null ? metadata.get("labels") : null, 20));
                if (metadata != null) {
                    appendSection(builder, "Components", limitedList(metadata.get("components"), 40));
                    appendSection(builder, "Relationships", limitedList(metadata.get("relationships"), 40));
                }
            }
        }
        appendRawLabelsAppendix(builder, metadata);
    }

    private static void appendStateDiagram(StringBuilder builder, String summary, Map<String, Object> metadata) {
        appendSection(builder, "Workflow Summary", stateSummary(metadata, summary));
        List<Map<String, Object>> nodes = mapList(metadata.get("nodes"));
        List<Map<String, Object>> edges = mapList(metadata.get("edges"));
        if (!nodes.isEmpty()) {
            builder.append("States\n\n| State | Description |\n|---|---|\n");
            Set<String> emittedStates = new LinkedHashSet<>();
            for (Map<String, Object> node : nodes) {
                String label = stringValue(node.get("label"));
                if (!label.isBlank() && emittedStates.add(label)) {
                    builder.append("| ").append(escapeTable(label)).append(" |  |\n");
                }
            }
            builder.append('\n');
        }
        if (!edges.isEmpty()) {
            builder.append("Transitions\n\n| From | Event | To |\n|---|---|---|\n");
            Set<String> emitted = new LinkedHashSet<>();
            for (Map<String, Object> edge : edges) {
                String from = stringValue(edge.get("sourceId"));
                String to = stringValue(edge.get("targetId"));
                String event = stringValue(edge.get("label"));
                if (from.isBlank() || to.isBlank() || !emitted.add(from + "\u0000" + event + "\u0000" + to)) {
                    continue;
                }
                builder.append("| ").append(escapeTable(from)).append(" | ")
                        .append(escapeTable(event)).append(" | ")
                        .append(escapeTable(to)).append(" |\n");
            }
            builder.append('\n');
        }
    }

    private static void appendClassDiagram(StringBuilder builder, String summary, Map<String, Object> metadata) {
        appendSection(builder, "Domain Summary", classSummary(metadata, summary));
        List<Map<String, Object>> nodes = mapList(metadata.get("nodes"));
        if (!nodes.isEmpty()) {
            builder.append("Classes\n\n| Class | Type | Attributes |\n|---|---|---|\n");
            Set<String> emittedClasses = new LinkedHashSet<>();
            for (Map<String, Object> node : nodes) {
                String label = stringValue(node.get("label"));
                if (!label.isBlank() && emittedClasses.add(label)) {
                    List<String> attributes = stringList(node.get("attributes"));
                    builder.append("| ").append(escapeTable(label)).append(" | ")
                            .append(escapeTable(classDisplayType(node))).append(" | ")
                            .append(escapeTable(String.join("; ", attributes))).append(" |\n");
                }
            }
            builder.append('\n');
        }
        appendRelationshipsTable(builder, "Relationships", metadata);
    }

    private static void appendArchitectureDiagram(StringBuilder builder, String summary, Map<String, Object> metadata) {
        appendSection(builder, "Architecture Summary", architectureSummary(metadata, summary));
        List<Map<String, Object>> nodes = mapList(metadata.get("nodes"));
        appendSection(builder, "Inbound", architectureLabels(nodes, "inbound"));
        appendSection(builder, "Controller Layer", architectureLabels(nodes, "controller-layer"));
        appendSection(builder, "Core Business Services", architectureLabels(nodes, "core-business-service"));
        appendSection(builder, "Supporting Services", architectureLabels(nodes, "supporting-service"));
        appendSection(builder, "Cross-Cutting Services", architectureLabels(nodes, "cross-cutting-service"));
        appendSection(builder, "Domain Logic", architectureLabels(nodes, "domain-logic"));
        appendSection(builder, "Data Mapping", architectureLabels(nodes, "data-mapping"));
        appendSection(builder, "Data Access", architectureLabels(nodes, "data-access"));
        appendSection(builder, "Infrastructure", architectureLabels(nodes, "infrastructure"));
        appendSection(builder, "External Clients", architectureLabels(nodes, "external-client"));
        appendSection(builder, "Database", architectureLabels(nodes, "database"));
    }

    private static void appendErDiagram(StringBuilder builder, String summary, Map<String, Object> metadata) {
        appendSection(builder, "Data Model Summary", summary);
        List<Map<String, Object>> nodes = mapList(metadata.get("nodes"));
        if (!nodes.isEmpty()) {
            builder.append("Tables\n\n| Table | Columns |\n|---|---|\n");
            Set<String> emitted = new LinkedHashSet<>();
            for (Map<String, Object> node : nodes) {
                String table = stringValue(node.get("label"));
                if (table.isBlank() || !emitted.add(table)) {
                    continue;
                }
                builder.append("| ").append(escapeTable(table)).append(" | ")
                        .append(escapeTable(String.join("; ", stringList(node.get("attributes"))))).append(" |\n");
            }
            builder.append('\n');
        } else {
            appendSection(builder, "Tables", limitedList(metadata.get("labels"), 40));
        }
        appendErRelationshipsTable(builder, metadata);
    }

    private static void appendErRelationshipsTable(StringBuilder builder, Map<String, Object> metadata) {
        List<Map<String, Object>> edges = mapList(metadata.get("edges"));
        if (edges.isEmpty()) {
            return;
        }
        builder.append("Relationships\n\n| Source Table | Target Table | Join/Reference |\n|---|---|---|\n");
        Set<String> emitted = new LinkedHashSet<>();
        for (Map<String, Object> edge : edges) {
            String source = stringValue(edge.get("sourceId"));
            String target = stringValue(edge.get("targetId"));
            String relationship = firstNonBlank(stringValue(edge.get("label")), "references");
            if (source.isBlank() || target.isBlank()
                    || !emitted.add(source + "\u0000" + target + "\u0000" + relationship)) {
                continue;
            }
            builder.append("| ").append(escapeTable(source)).append(" | ")
                    .append(escapeTable(target)).append(" | ")
                    .append(escapeTable(relationship)).append(" |\n");
        }
        builder.append('\n');
    }

    private static void appendRelationshipsTable(StringBuilder builder, String heading, Map<String, Object> metadata) {
        List<Map<String, Object>> edges = mapList(metadata.get("edges"));
        if (edges.isEmpty()) {
            return;
        }
        builder.append(heading).append("\n\n| Source | Relationship | Target |\n|---|---|---|\n");
        Set<String> emitted = new LinkedHashSet<>();
        for (Map<String, Object> edge : edges) {
            String source = stringValue(edge.get("sourceId"));
            String target = stringValue(edge.get("targetId"));
            String relationship = firstNonBlank(
                    stringValue(edge.get("label")),
                    stringValue(edge.get("relationshipType")),
                    "connects");
            if (source.isBlank() || target.isBlank()
                    || !emitted.add(source + "\u0000" + relationship + "\u0000" + target)) {
                continue;
            }
            builder.append("| ")
                    .append(escapeTable(source)).append(" | ")
                    .append(escapeTable(relationship)).append(" | ")
                    .append(escapeTable(target)).append(" |\n");
        }
        builder.append('\n');
    }

    private static void appendRawLabelsAppendix(StringBuilder builder, Map<String, Object> metadata) {
        List<String> labels = stringList(metadata != null ? metadata.get("labels") : null);
        if (labels.isEmpty()) {
            return;
        }
        builder.append("<details>\n<summary>Raw extracted labels</summary>\n\n");
        labels.stream()
                .filter(label -> !isRawDrawioId(label))
                .distinct()
                .limit(MAX_RAW_LABELS)
                .forEach(label -> builder.append("- ").append(label).append('\n'));
        builder.append("\n</details>\n\n");
    }

    private static void appendMetadataSections(StringBuilder builder, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        appendSection(builder, "Title", metadata.get("title"));
        appendSection(builder, "Description", metadata.get("description"));
        appendSection(builder, "Purpose", metadata.get("purpose"));
        appendSection(builder, "Extracted Text", metadata.get("visibleText"));
        appendSection(builder, "Labels", metadata.get("labels"));
        appendSection(builder, "Components", metadata.get("components"));
        appendSection(builder, "Relationships", metadata.get("relationships"));
        appendSection(builder, "Flow", firstPresent(metadata, "userActions", "flow"));
        appendSection(builder, "Fields", metadata.get("fields"));
        appendSection(builder, "Warnings", metadata.get("warnings"));
    }

    private static boolean isDiagram(AttachmentManifestEntry attachment, Map<String, Object> metadata) {
        AttachmentType type = diagramType(attachment, metadata);
        return switch (type) {
            case DRAWIO_SOURCE, DRAWIO_DIAGRAM, STATE_DIAGRAM, CLASS_DIAGRAM, ARCHITECTURE_DIAGRAM, ER_DIAGRAM,
                    GENERIC_DIAGRAM, FLOWCHART, SEQUENCE_DIAGRAM, SVG_DIAGRAM, VISIO_DIAGRAM ->
                true;
            default -> false;
        };
    }

    private static AttachmentType diagramType(AttachmentManifestEntry attachment, Map<String, Object> metadata) {
        if (metadata != null && metadata.get("diagramType") instanceof String value) {
            try {
                return AttachmentType.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                // Fall through to detected type.
            }
        }
        return attachment.getDetectedType();
    }

    private static boolean hasSearchableContent(
            String searchableSummary,
            Map<String, Object> metadata,
            AttachmentType type) {
        if (metadata != null && metadata.values().stream().anyMatch(MarkdownAttachmentEnrichmentService::hasValue)) {
            return true;
        }
        if (searchableSummary == null || searchableSummary.isBlank()) {
            return false;
        }
        return type != AttachmentType.GENERIC_IMAGE || !searchableSummary.startsWith("Image attachment:");
    }

    private static Object firstPresent(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (hasValue(value)) {
                return value;
            }
        }
        return null;
    }

    private static void appendLine(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(label).append(": ").append(value.strip()).append("\n\n");
        }
    }

    private static void appendSection(StringBuilder builder, String heading, Object value) {
        if (!hasValue(value)) {
            return;
        }
        builder.append(heading).append("\n\n");
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && !item.toString().isBlank()) {
                    builder.append("- ").append(item.toString().strip()).append('\n');
                }
            }
            builder.append('\n');
        } else {
            builder.append(value.toString().strip()).append("\n\n");
        }
    }

    private static List<?> limitedList(Object value, int max) {
        List<String> values = stringList(value);
        return values.stream().limit(max).toList();
    }

    private static List<String> deduplicatedStringList(Object value, int max) {
        return stringList(value).stream()
                .filter(label -> !isRawDrawioId(label))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .limit(max)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> maps = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                maps.add((Map<String, Object>) map);
            }
        }
        return maps;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : iterable) {
            if (item != null && !item.toString().isBlank()) {
                values.add(item.toString().strip());
            }
        }
        return values;
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString().strip() : "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static boolean containsAny(String value, String... needles) {
        String lower = value.toLowerCase();
        for (String needle : needles) {
            if (lower.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String escapeTable(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace("\n", " ");
    }

    private static boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return !text.isBlank();
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && !item.toString().isBlank()) {
                    return true;
                }
            }
            return false;
        }
        return !value.toString().isBlank();
    }

    private static boolean isValidDiagramMetadata(AttachmentType type, Map<String, Object> metadata) {
        if (metadata == null) {
            return true;
        }
        Set<String> nodeLabels = new LinkedHashSet<>();
        for (Map<String, Object> node : mapList(metadata.get("nodes"))) {
            String label = stringValue(node.get("label"));
            if (label.isBlank() || isRawDrawioId(label) || isMultiplicity(label)) {
                return false;
            }
            if (type == AttachmentType.CLASS_DIAGRAM && isClassAttribute(label)) {
                return false;
            }
            nodeLabels.add(label);
        }
        Set<String> relationshipKeys = new LinkedHashSet<>();
        for (Map<String, Object> edge : mapList(metadata.get("edges"))) {
            String source = stringValue(edge.get("sourceId"));
            String target = stringValue(edge.get("targetId"));
            if (source.isBlank() || target.isBlank() || isRawDrawioId(source) || isRawDrawioId(target)) {
                return false;
            }
            if (!relationshipKeys.add(source + "\u0000" + stringValue(edge.get("label")) + "\u0000" + target)) {
                return false;
            }
        }
        return true;
    }

    private static String conciseDiagramSummary(AttachmentType type, Map<String, Object> metadata) {
        List<String> labels = deduplicatedStringList(metadata != null ? metadata.get("labels") : null, 12);
        if (labels.isEmpty()) {
            return "Diagram structure could not be resolved reliably.";
        }
        return displayType(type) + " containing: " + String.join(", ", labels) + ".";
    }

    private static String stateSummary(Map<String, Object> metadata, String fallback) {
        List<Map<String, Object>> edges = mapList(metadata.get("edges"));
        if (edges.isEmpty()) {
            return fallback;
        }
        String start = edges.stream()
                .map(edge -> stringValue(edge.get("sourceId")))
                .filter(source -> !"START".equals(source) && !source.isBlank())
                .findFirst()
                .orElse("");
        List<String> terminal = edges.stream()
                .map(edge -> stringValue(edge.get("targetId")))
                .filter(target -> !"END".equals(target) && !target.isBlank())
                .distinct()
                .limit(4)
                .toList();
        if (start.isBlank()) {
            return fallback;
        }
        return "Workflow begins in " + start
                + (terminal.isEmpty() ? "." : " and can move to " + String.join(", ", terminal) + ".");
    }

    private static String classSummary(Map<String, Object> metadata, String fallback) {
        List<Map<String, Object>> edges = mapList(metadata.get("edges"));
        List<String> associations = edges.stream()
                .filter(edge -> containsAny(relationshipText(edge), "association", "aggregation", "composition"))
                .map(edge -> stringValue(edge.get("sourceId")) + " relates to " + stringValue(edge.get("targetId")))
                .filter(text -> !text.isBlank())
                .limit(2)
                .toList();
        List<String> inheritance = edges.stream()
                .filter(edge -> containsAny(relationshipText(edge), "extends", "implements"))
                .map(edge -> stringValue(edge.get("sourceId")) + " " + relationshipText(edge) + " " + stringValue(edge.get("targetId")))
                .limit(2)
                .toList();
        List<String> facts = new ArrayList<>();
        facts.addAll(associations);
        facts.addAll(inheritance);
        return facts.isEmpty() ? fallback : String.join("; ", facts) + ".";
    }

    private static String relationshipText(Map<String, Object> edge) {
        return firstNonBlank(stringValue(edge.get("relationshipType")), stringValue(edge.get("label")));
    }

    private static String classDisplayType(Map<String, Object> node) {
        List<String> stereotypes = stringList(node.get("stereotypes"));
        boolean isAbstract = stereotypes.stream()
                .map(String::toLowerCase)
                .anyMatch(value -> value.contains("abstract"));
        return isAbstract ? "Abstract class" : "Class";
    }

    private static String architectureSummary(Map<String, Object> metadata, String fallback) {
        List<Map<String, Object>> nodes = mapList(metadata.get("nodes"));
        List<String> types = nodes.stream()
                .map(node -> stringValue(node.get("type")))
                .filter(type -> !type.isBlank())
                .distinct()
                .toList();
        if (types.isEmpty()) {
            return fallback;
        }
        return "Architecture diagram containing " + String.join(", ", types).replace("-", " ") + ".";
    }

    private static List<String> architectureLabels(List<Map<String, Object>> nodes, String... types) {
        Set<String> wanted = Set.of(types);
        return nodes.stream()
                .filter(node -> wanted.contains(stringValue(node.get("type"))))
                .map(node -> stringValue(node.get("label")))
                .filter(label -> !label.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .limit(40)
                .toList();
    }

    private static boolean isRawDrawioId(String value) {
        return value != null && RAW_DRAWIO_ID.matcher(value.strip()).matches();
    }

    private static boolean isMultiplicity(String value) {
        return value != null && value.strip().matches("(?:0\\.\\.1|0\\.\\.\\*|1\\.\\.\\*|1|\\*|many|one)");
    }

    private static boolean isClassAttribute(String value) {
        return value != null && value.strip().matches("[-+#~]?\\s*(?:[A-Z][A-Za-z0-9_<>?, ]*|Collection<[^>]+>|List<[^>]+>|Set<[^>]+>|Map<[^>]+>)\\s+[a-z][A-Za-z0-9_]*");
    }

    private static boolean isLikelyErdImage(AttachmentManifestEntry attachment) {
        String fileName = attachment.getFileName() != null ? attachment.getFileName().toLowerCase() : "";
        return attachment.getDetectedType() == AttachmentType.GENERIC_IMAGE
                && (fileName.contains("erd") || fileName.contains("er-diagram")
                || fileName.contains("schema") || fileName.contains("database"));
    }

    private static String displayType(AttachmentType type) {
        if (type == null) {
            return "Unknown";
        }
        String[] words = type.name().toLowerCase().split("_");
        List<String> formatted = new ArrayList<>();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            formatted.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return String.join(" ", formatted);
    }

    private static String normalizePath(String rawPath) {
        String path = rawPath.strip().replace('\\', '/');
        if (path.startsWith("./")) {
            path = path.substring(2);
        }
        return path;
    }
}
