package com.confluence.ingestor.attachment;

import com.confluence.ingestor.transform.DrawioExtractor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

@Component
public class DrawioAttachmentDetector {

    private static final Pattern EMBEDDED_DRAWIO_IN_PNG =
            Pattern.compile("(?:mxfile|mxGraphModel)", Pattern.CASE_INSENSITIVE);

    private final DrawioExtractor drawioExtractor;

    public DrawioAttachmentDetector(DrawioExtractor drawioExtractor) {
        this.drawioExtractor = drawioExtractor;
    }

    public AttachmentDetectionResult detect(String fileName, String mimeType, byte[] content) {
        String extension = extensionOf(fileName);
        if ("drawio".equals(extension) || "xml".equals(extension)) {
            return detectSourceXml(fileName, content);
        }
        if (isPng(extension, mimeType, content)) {
            return detectEmbeddedPng(fileName, content);
        }
        return null;
    }

    private AttachmentDetectionResult detectSourceXml(String fileName, byte[] content) {
        String xml = new String(content, StandardCharsets.UTF_8);
        if (!drawioExtractor.looksLikeDrawioXml(xml)) {
            return null;
        }
        DiagramModel model = drawioExtractor.extractModel(xml, null, fileName);
        Map<String, Object> metadata = buildMetadata(model, false);
        String summary = model.labels().isEmpty()
                ? "Draw.io source diagram"
                : summaryPrefix(model.diagramType()) + ": " + String.join(", ", firstLabels(model.labels()));
        return AttachmentDetectionResult.of(model.diagramType(), DetectionMethod.FORMAT_PARSER, summary, metadata);
    }

    private AttachmentDetectionResult detectEmbeddedPng(String fileName, byte[] content) {
        String embeddedXml = extractEmbeddedDrawioXmlFromPngChunks(content);
        if (embeddedXml == null) {
            String asText = new String(content, StandardCharsets.ISO_8859_1);
            if (!EMBEDDED_DRAWIO_IN_PNG.matcher(asText).find()) {
                return null;
            }
            embeddedXml = extractEmbeddedDrawioXml(asText);
        }
        if (embeddedXml == null) {
            return null;
        }
        DiagramModel model = drawioExtractor.extractModel(embeddedXml, null, fileName);
        Map<String, Object> metadata = buildMetadata(model, true);
        String summary = model.labels().isEmpty()
                ? "Draw.io exported PNG diagram"
                : summaryPrefix(model.diagramType()) + ": " + String.join(", ", firstLabels(model.labels()));
        return AttachmentDetectionResult.of(
                model.diagramType(), DetectionMethod.EMBEDDED_METADATA, summary, metadata);
    }

    private static boolean isPng(String extension, String mimeType, byte[] content) {
        if ("png".equals(extension) || "image/png".equalsIgnoreCase(mimeType)) {
            return true;
        }
        return content != null
                && content.length >= 4
                && content[0] == (byte) 0x89
                && content[1] == 0x50
                && content[2] == 0x4E
                && content[3] == 0x47;
    }

    private static String extractEmbeddedDrawioXml(String pngText) {
        int mxFileStart = pngText.indexOf("<mxfile");
        if (mxFileStart < 0) {
            mxFileStart = pngText.indexOf("<mxGraphModel");
        }
        if (mxFileStart < 0) {
            return null;
        }
        int end = pngText.indexOf("</mxfile>", mxFileStart);
        if (end > mxFileStart) {
            return pngText.substring(mxFileStart, end + "</mxfile>".length());
        }
        end = pngText.indexOf("</mxGraphModel>", mxFileStart);
        if (end > mxFileStart) {
            return pngText.substring(mxFileStart, end + "</mxGraphModel>".length());
        }
        return pngText.substring(mxFileStart, Math.min(pngText.length(), mxFileStart + 500_000));
    }

    private static String extractEmbeddedDrawioXmlFromPngChunks(byte[] content) {
        int offset = 8;
        while (offset + 12 <= content.length) {
            int length = readInt(content, offset);
            if (length < 0 || offset + 12 + length > content.length) {
                break;
            }
            String chunkType = new String(content, offset + 4, 4, StandardCharsets.US_ASCII);
            if ("tEXt".equals(chunkType) || "iTXt".equals(chunkType) || "zTXt".equals(chunkType)) {
                byte[] chunkBytes = java.util.Arrays.copyOfRange(content, offset + 8, offset + 8 + length);
                String chunkData = decodeTextChunk(chunkType, chunkBytes);
                String xml = chunkData != null ? extractEmbeddedDrawioXml(chunkData) : null;
                if (xml != null) {
                    return xml;
                }
                xml = chunkData != null ? extractUrlEncodedDrawioXml(chunkData) : null;
                if (xml != null) {
                    return xml;
                }
            }
            offset += 12 + length;
        }
        return null;
    }

    private static String decodeTextChunk(String chunkType, byte[] chunkBytes) {
        if ("zTXt".equals(chunkType)) {
            int keyEnd = indexOf(chunkBytes, (byte) 0);
            if (keyEnd < 0 || keyEnd + 2 >= chunkBytes.length) {
                return null;
            }
            byte[] inflated = inflate(java.util.Arrays.copyOfRange(chunkBytes, keyEnd + 2, chunkBytes.length));
            if (inflated == null) {
                return null;
            }
            String key = new String(chunkBytes, 0, keyEnd, StandardCharsets.ISO_8859_1);
            return key + '\0' + new String(inflated, StandardCharsets.ISO_8859_1);
        }
        return new String(chunkBytes, StandardCharsets.ISO_8859_1);
    }

    private static String extractUrlEncodedDrawioXml(String chunkData) {
        int valueStart = chunkData.indexOf('\0');
        if (valueStart < 0 || valueStart == chunkData.length() - 1) {
            return null;
        }
        String key = chunkData.substring(0, valueStart);
        if (!"mxfile".equalsIgnoreCase(key) && !"mxGraphModel".equalsIgnoreCase(key)) {
            return null;
        }
        String encoded = chunkData.substring(valueStart + 1);
        String decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        return extractEmbeddedDrawioXml(decoded);
    }

    private static byte[] inflate(byte[] compressed) {
        try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(compressed));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        } catch (Exception ex) {
            return null;
        }
    }

    private static int indexOf(byte[] bytes, byte value) {
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private static Map<String, Object> buildMetadata(DiagramModel model, boolean embeddedDrawio) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("embeddedDrawio", embeddedDrawio);
        metadata.put("diagramName", model.diagramName());
        metadata.put("diagramType", model.diagramType().name());
        metadata.put("labels", model.labels());
        metadata.put("labelCount", model.labels().size());
        metadata.put("nodes", model.nodes().stream().map(DrawioAttachmentDetector::nodeMap).toList());
        metadata.put("edges", model.edges().stream().map(DrawioAttachmentDetector::edgeMap).toList());
        metadata.put("nodeCount", model.nodes().size());
        metadata.put("edgeCount", model.edges().size());
        metadata.put("components", model.nodes().stream()
                .map(DiagramNode::label)
                .filter(label -> label != null && !label.isBlank())
                .toList());
        metadata.put("relationships", model.edges().stream()
                .map(DrawioAttachmentDetector::relationshipText)
                .filter(text -> text != null && !text.isBlank())
                .toList());
        return metadata;
    }

    private static Map<String, Object> nodeMap(DiagramNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "id", node.id());
        putIfPresent(map, "label", node.label());
        putIfPresent(map, "type", node.type());
        putIfPresent(map, "parentId", node.parentId());
        if (node.attributes() != null && !node.attributes().isEmpty()) {
            map.put("attributes", node.attributes());
        }
        if (node.methods() != null && !node.methods().isEmpty()) {
            map.put("methods", node.methods());
        }
        if (node.stereotypes() != null && !node.stereotypes().isEmpty()) {
            map.put("stereotypes", node.stereotypes());
        }
        map.put("order", node.order());
        return map;
    }

    private static Map<String, Object> edgeMap(DiagramEdge edge) {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "sourceId", edge.sourceId());
        putIfPresent(map, "targetId", edge.targetId());
        putIfPresent(map, "label", edge.label());
        putIfPresent(map, "relationshipType", edge.relationshipType());
        putIfPresent(map, "sourceMultiplicity", edge.sourceMultiplicity());
        putIfPresent(map, "targetMultiplicity", edge.targetMultiplicity());
        putIfPresent(map, "parentId", edge.parentId());
        map.put("order", edge.order());
        return map;
    }

    private static String relationshipText(DiagramEdge edge) {
        if (edge.sourceId() == null && edge.targetId() == null) {
            return edge.label();
        }
        if (edge.sourceId() == null || edge.targetId() == null) {
            return null;
        }
        String label = edge.label() != null ? edge.label() : "connects";
        return "%s -> %s -> %s".formatted(edge.sourceId(), label, edge.targetId());
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private static String summaryPrefix(AttachmentType type) {
        return switch (type) {
            case STATE_DIAGRAM -> "State diagram";
            case CLASS_DIAGRAM -> "Class diagram";
            case ARCHITECTURE_DIAGRAM -> "Architecture diagram";
            case ER_DIAGRAM -> "ER diagram";
            default -> "Draw.io diagram";
        };
    }

    private static List<String> firstLabels(List<String> labels) {
        return labels.subList(0, Math.min(10, labels.size()));
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
