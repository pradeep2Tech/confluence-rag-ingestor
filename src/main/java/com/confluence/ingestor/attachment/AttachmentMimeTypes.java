package com.confluence.ingestor.attachment;

import java.util.Locale;
import java.util.Map;

final class AttachmentMimeTypes {

    private AttachmentMimeTypes() {
    }

    static String guessFromExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return null;
        }
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "svg" -> "image/svg+xml";
            case "drawio" -> "application/vnd.jgraph.mxfile";
            case "vsdx" -> "application/vnd.ms-visio.drawing";
            case "vsd" -> "application/vnd.visio";
            case "pdf" -> "application/pdf";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> null;
        };
    }

    static String guessFromMagicBytes(byte[] content) {
        if (content == null || content.length < 4) {
            return null;
        }
        if (content[0] == (byte) 0x89 && content[1] == 0x50 && content[2] == 0x4E && content[3] == 0x47) {
            return "image/png";
        }
        if (content[0] == (byte) 0xFF && content[1] == (byte) 0xD8) {
            return "image/jpeg";
        }
        if (content[0] == 0x47 && content[1] == 0x49 && content[2] == 0x46) {
            return "image/gif";
        }
        if (content.length >= 12
                && content[0] == 0x52
                && content[1] == 0x49
                && content[2] == 0x46
                && content[3] == 0x46
                && content[8] == 0x57
                && content[9] == 0x45
                && content[10] == 0x42
                && content[11] == 0x50) {
            return "image/webp";
        }
        if (content[0] == 0x25 && content[1] == 0x50 && content[2] == 0x44 && content[3] == 0x46) {
            return "application/pdf";
        }
        if (content[0] == 0x50 && content[1] == 0x4B && content[2] == 0x03 && content[3] == 0x04) {
            return "application/zip";
        }
        return null;
    }

    static String effectiveMimeType(String fileName, String declaredMimeType, byte[] content) {
        if (declaredMimeType != null && !declaredMimeType.isBlank()) {
            return declaredMimeType;
        }
        String extension = extensionOf(fileName);
        String fromExtension = guessFromExtension(extension);
        if (fromExtension != null) {
            return fromExtension;
        }
        return guessFromMagicBytes(content);
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
