package com.confluence.ingestor.api.dto;

/**
 * Response for {@code GET /api/config} — secrets are masked.
 */
public record ConfigResponse(
        String llmProvider,
        String llmModel,
        String ollamaBaseUrl,
        String embeddingModel,
        String confluenceBaseUrl,
        String confluenceTarget,
        String parentPageId,
        String spaceKey,
        String vectorStore,
        String chromaHost,
        Integer chromaPort,
        String chromaCollectionName,
        boolean verifySsl,
        String maskedPat,
        boolean patConfigured
) {
}
