package com.confluence.ingestor.attachment;

import java.util.Map;

public record AttachmentDetectionResult(
        AttachmentType type,
        DetectionMethod method,
        String searchableSummary,
        Map<String, Object> extractedMetadata,
        Double confidence) {

    public static AttachmentDetectionResult of(
            AttachmentType type, DetectionMethod method, String summary, Map<String, Object> metadata) {
        return new AttachmentDetectionResult(type, method, summary, metadata, null);
    }

    public static AttachmentDetectionResult unknown() {
        return new AttachmentDetectionResult(AttachmentType.UNKNOWN, DetectionMethod.UNKNOWN, null, null, null);
    }
}
