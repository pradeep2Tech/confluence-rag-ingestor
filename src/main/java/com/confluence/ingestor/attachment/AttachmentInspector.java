package com.confluence.ingestor.attachment;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class AttachmentInspector {

    private static final Set<String> VISION_ELIGIBLE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp");

    private final DrawioAttachmentDetector drawioDetector;
    private final VisioAttachmentDetector visioDetector;
    private final SvgAttachmentDetector svgDetector;
    private final PdfAttachmentDetector pdfDetector;
    private final PptxAttachmentDetector pptxDetector;

    public AttachmentInspector(
            DrawioAttachmentDetector drawioDetector,
            VisioAttachmentDetector visioDetector,
            SvgAttachmentDetector svgDetector,
            PdfAttachmentDetector pdfDetector,
            PptxAttachmentDetector pptxDetector) {
        this.drawioDetector = drawioDetector;
        this.visioDetector = visioDetector;
        this.svgDetector = svgDetector;
        this.pdfDetector = pdfDetector;
        this.pptxDetector = pptxDetector;
    }

    public AttachmentDetectionResult inspectDeterministic(String fileName, String declaredMimeType, byte[] content) {
        String mimeType = AttachmentMimeTypes.effectiveMimeType(fileName, declaredMimeType, content);
        String extension = extensionOf(fileName);

        AttachmentDetectionResult byExtension = detectByExtension(extension, fileName);
        if (byExtension != null && byExtension.type() != AttachmentType.UNKNOWN) {
            return byExtension;
        }

        AttachmentDetectionResult result = firstNonNull(
                drawioDetector.detect(fileName, mimeType, content),
                visioDetector.detect(fileName, mimeType, content),
                svgDetector.detect(fileName, mimeType, content),
                pdfDetector.detect(fileName, mimeType, content),
                pptxDetector.detect(fileName, mimeType, content));

        if (result != null) {
            return result;
        }

        if (mimeType != null && mimeType.startsWith("image/")) {
            return AttachmentDetectionResult.of(
                    AttachmentType.GENERIC_IMAGE,
                    DetectionMethod.MIME_TYPE,
                    "Image attachment: " + fileName,
                    null);
        }

        return AttachmentDetectionResult.unknown();
    }

    public boolean requiresVision(AttachmentDetectionResult deterministic) {
        if (deterministic == null || deterministic.type() == null) {
            return true;
        }
        return deterministic.type() == AttachmentType.GENERIC_IMAGE
                || deterministic.type() == AttachmentType.UNKNOWN;
    }

    public boolean isVisionEligible(String fileName, byte[] content) {
        String extension = extensionOf(fileName);
        if (VISION_ELIGIBLE_EXTENSIONS.contains(extension)) {
            return true;
        }
        String mime = AttachmentMimeTypes.guessFromMagicBytes(content);
        return mime != null && mime.startsWith("image/") && !"image/svg+xml".equals(mime);
    }

    private static AttachmentDetectionResult detectByExtension(String extension, String fileName) {
        if (extension.isBlank()) {
            return null;
        }
        return switch (extension) {
            case "drawio" -> null;
            case "vsdx", "vsd" -> null;
            case "svg" -> null;
            case "pdf" -> null;
            case "pptx", "ppt" -> null;
            case "png", "jpg", "jpeg", "gif", "webp", "bmp" -> null;
            default -> AttachmentDetectionResult.of(
                    AttachmentType.OTHER, DetectionMethod.EXTENSION, "Attachment: " + fileName, null);
        };
    }

    @SafeVarargs
    private static AttachmentDetectionResult firstNonNull(AttachmentDetectionResult... results) {
        for (AttachmentDetectionResult result : results) {
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
