package com.confluence.ingestor.attachment;

import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class PptxAttachmentDetector {

    public AttachmentDetectionResult detect(String fileName, String mimeType, byte[] content) {
        String extension = extensionOf(fileName);
        boolean byExtension = "pptx".equals(extension) || "ppt".equals(extension);
        boolean byMime = mimeType != null && mimeType.toLowerCase(Locale.ROOT).contains("presentation");
        if (!byExtension && !byMime) {
            return null;
        }

        if ("pptx".equals(extension) && content != null && isZip(content) && hasPptxStructure(content)) {
            return AttachmentDetectionResult.of(
                    AttachmentType.PRESENTATION,
                    DetectionMethod.FORMAT_PARSER,
                    "PowerPoint presentation: " + fileName,
                    Map.of("fileName", fileName, "format", "pptx"));
        }

        DetectionMethod method = byMime ? DetectionMethod.MIME_TYPE : DetectionMethod.EXTENSION;
        return AttachmentDetectionResult.of(
                AttachmentType.PRESENTATION, method, "Presentation: " + fileName, Map.of("fileName", fileName));
    }

    private static boolean isZip(byte[] content) {
        return content.length >= 4
                && content[0] == 0x50
                && content[1] == 0x4B
                && content[2] == 0x03
                && content[3] == 0x04;
    }

    private static boolean hasPptxStructure(byte[] content) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (name.startsWith("ppt/")) {
                    return true;
                }
            }
        } catch (Exception ex) {
            return false;
        }
        return false;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
