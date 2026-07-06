package com.confluence.ingestor.messaging;

import com.confluence.ingestor.api.dto.IngestionRequest;

/**
 * Kafka payload for ingestion stage handoff. Key: {@code parentPageId}.
 */
public record IngestionJobMessage(
        IngestionJobType jobType,
        String parentPageId,
        IngestionRequest request,
        String messageId) {

    public static IngestionJobMessage of(IngestionJobType jobType, IngestionRequest request) {
        return new IngestionJobMessage(
                jobType,
                request.parentPageId(),
                request,
                java.util.UUID.randomUUID().toString());
    }
}
