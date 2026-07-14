package com.confluence.ingestor.attachment;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class SvgAttachmentDetector {

    public AttachmentDetectionResult detect(String fileName, String mimeType, byte[] content) {
        String extension = extensionOf(fileName);
        if (!"svg".equals(extension) && !"image/svg+xml".equalsIgnoreCase(mimeType)) {
            return null;
        }
        if (content == null || content.length == 0) {
            return null;
        }

        try {
            Document document = parseSecure(content);
            Element root = document.getDocumentElement();
            if (root == null || !"svg".equalsIgnoreCase(root.getTagName())) {
                return null;
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("title", textOfFirst(root, "title"));
            metadata.put("description", textOfFirst(root, "desc"));
            metadata.put("ids", collectIds(root));
            List<String> visibleText = collectVisibleText(root);
            metadata.put("visibleText", visibleText);

            String summary = buildSummary(metadata, visibleText);
            return AttachmentDetectionResult.of(
                    AttachmentType.SVG_DIAGRAM, DetectionMethod.FORMAT_PARSER, summary, metadata);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Document parseSecure(byte[] content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(content));
    }

    private static String textOfFirst(Element root, String tag) {
        NodeList nodes = root.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        return text != null ? text.strip() : null;
    }

    private static List<String> collectIds(Element root) {
        Set<String> ids = new LinkedHashSet<>();
        collectIdsRecursive(root, ids);
        return new ArrayList<>(ids);
    }

    private static void collectIdsRecursive(Node node, Set<String> ids) {
        if (node instanceof Element element) {
            String id = element.getAttribute("id");
            if (id != null && !id.isBlank()) {
                ids.add(id.strip());
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectIdsRecursive(children.item(i), ids);
        }
    }

    private static List<String> collectVisibleText(Element root) {
        Set<String> texts = new LinkedHashSet<>();
        NodeList textNodes = root.getElementsByTagName("text");
        for (int i = 0; i < textNodes.getLength(); i++) {
            String text = textNodes.item(i).getTextContent();
            if (text != null && !text.isBlank()) {
                texts.add(text.replace('\n', ' ').strip());
            }
        }
        NodeList tspanNodes = root.getElementsByTagName("tspan");
        for (int i = 0; i < tspanNodes.getLength(); i++) {
            String text = tspanNodes.item(i).getTextContent();
            if (text != null && !text.isBlank()) {
                texts.add(text.replace('\n', ' ').strip());
            }
        }
        return new ArrayList<>(texts);
    }

    private static String buildSummary(Map<String, Object> metadata, List<String> visibleText) {
        Object title = metadata.get("title");
        if (title instanceof String titleText && !titleText.isBlank()) {
            return "SVG diagram: " + titleText;
        }
        if (!visibleText.isEmpty()) {
            return "SVG diagram: " + String.join(", ", visibleText.subList(0, Math.min(8, visibleText.size())));
        }
        return "SVG diagram";
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
