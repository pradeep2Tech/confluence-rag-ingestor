package com.confluence.ingestor.transform;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Locates draw.io macros in Confluence {@code body.storage} XHTML.
 */
public final class StorageDrawioReferenceExtractor {

    public static final String DIAGRAMS_ASSET_PREFIX = "assets/diagrams/";

    private StorageDrawioReferenceExtractor() {
    }

    public static List<DrawioReference> extractReferences(String storageHtml) {
        if (storageHtml == null || storageHtml.isBlank()) {
            return List.of();
        }

        Document document = Jsoup.parse(wrapStorageHtml(storageHtml), "", Parser.xmlParser());
        Element root = document.selectFirst("root");
        if (root == null) {
            return List.of();
        }
        return extractReferences(root);
    }

    public static List<DrawioReference> extractReferences(Element root) {
        List<DrawioReference> references = new ArrayList<>();
        for (Element macro : root.select("ac|structured-macro, structured-macro")) {
            if (!isDrawioMacro(macro)) {
                continue;
            }
            references.add(fromMacro(macro));
        }
        return references;
    }

    public static DrawioReference fromMacro(Element macro) {
        return new DrawioReference(
                parameterValue(macro, "diagramName"),
                attachmentFilename(macro),
                embeddedXml(macro),
                macro);
    }

    private static boolean isDrawioMacro(Element macro) {
        String name = macro.attr("ac:name");
        if (name.isBlank()) {
            name = macro.attr("name");
        }
        return "drawio".equalsIgnoreCase(name);
    }

    private static String parameterValue(Element macro, String parameterName) {
        for (Element parameter : macro.select("ac|parameter, parameter")) {
            String name = parameter.attr("ac:name");
            if (name.isBlank()) {
                name = parameter.attr("name");
            }
            if (parameterName.equalsIgnoreCase(name)) {
                return parameter.text().strip();
            }
        }
        return "";
    }

    private static String attachmentFilename(Element macro) {
        Element attachment = macro.selectFirst("ri|attachment");
        if (attachment == null) {
            attachment = macro.selectFirst("attachment");
        }
        if (attachment == null) {
            return "";
        }
        String filename = attachment.attr("ri:filename");
        if (filename.isBlank()) {
            filename = attachment.attr("filename");
        }
        return filename.strip();
    }

    private static String embeddedXml(Element macro) {
        Element body = macro.selectFirst("ac|plain-text-body");
        if (body == null) {
            body = macro.selectFirst("plain-text-body");
        }
        if (body == null) {
            return "";
        }
        String innerHtml = body.html().strip();
        if (!innerHtml.isBlank()) {
            return innerHtml;
        }
        return body.text().strip();
    }

    private static String wrapStorageHtml(String storageHtml) {
        String trimmed = storageHtml.strip();
        if (trimmed.startsWith("<")) {
            return "<root>" + trimmed + "</root>";
        }
        return "<root><p>" + trimmed + "</p></root>";
    }
}
