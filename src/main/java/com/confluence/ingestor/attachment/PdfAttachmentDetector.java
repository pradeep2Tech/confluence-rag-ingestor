package com.confluence.ingestor.attachment;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class PdfAttachmentDetector {

    public AttachmentDetectionResult detect(String fileName, String mimeType, byte[] content) {
        String extension = extensionOf(fileName);
        boolean byExtension = "pdf".equals(extension);
        boolean byMime = mimeType != null && mimeType.toLowerCase(Locale.ROOT).contains("pdf");
        boolean byMagic = content != null
                && content.length >= 4
                && content[0] == 0x25
                && content[1] == 0x50
                && content[2] == 0x44
                && content[3] == 0x46;

        if (!byExtension && !byMime && !byMagic) {
            return null;
        }

        DetectionMethod method = byMagic ? DetectionMethod.MAGIC_BYTES
                : byMime ? DetectionMethod.MIME_TYPE
                : DetectionMethod.EXTENSION;
        return AttachmentDetectionResult.of(
                AttachmentType.PDF_DOCUMENT, method, "PDF document: " + fileName, Map.of("fileName", fileName));
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
