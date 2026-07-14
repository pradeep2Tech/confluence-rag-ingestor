package com.confluence.ingestor.attachment;

import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class VisioAttachmentDetector {

    public AttachmentDetectionResult detect(String fileName, String mimeType, byte[] content) {
        String extension = extensionOf(fileName);
        if ("vsdx".equals(extension)) {
            return detectVsdx(content);
        }
        if ("vsd".equals(extension) || "application/vnd.visio".equalsIgnoreCase(mimeType)) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("format", "vsd");
            metadata.put("note", "Binary VSD parser not implemented");
            return AttachmentDetectionResult.of(
                    AttachmentType.VISIO_SOURCE,
                    DetectionMethod.EXTENSION,
                    "Visio binary source file (.vsd)",
                    metadata);
        }
        return null;
    }

    private AttachmentDetectionResult detectVsdx(byte[] content) {
        if (content == null || content.length < 4) {
            return null;
        }
        boolean hasPages = false;
        boolean hasDocument = false;
        boolean hasMasters = false;
        int pageCount = 0;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (name.startsWith("visio/pages/") && name.endsWith(".xml")) {
                    hasPages = true;
                    pageCount++;
                } else if ("visio/document.xml".equals(name)) {
                    hasDocument = true;
                } else if (name.startsWith("visio/masters/")) {
                    hasMasters = true;
                }
            }
        } catch (Exception ex) {
            return null;
        }

        if (!hasPages && !hasDocument) {
            return null;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("hasPages", hasPages);
        metadata.put("hasDocument", hasDocument);
        metadata.put("hasMasters", hasMasters);
        metadata.put("pageCount", pageCount);

        AttachmentType type = pageCount > 0 ? AttachmentType.VISIO_DIAGRAM : AttachmentType.VISIO_SOURCE;
        String summary = pageCount > 0
                ? "Visio diagram with " + pageCount + " page(s)"
                : "Visio source document";
        return AttachmentDetectionResult.of(type, DetectionMethod.FORMAT_PARSER, summary, metadata);
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
