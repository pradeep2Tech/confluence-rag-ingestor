package com.confluence.ingestor.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/config}.
 */
public record ConfigRequest(
        @Size(max = 64)
        String llmProvider,

        @Size(max = 128)
        String llmModel,

        @Size(max = 256)
        String ollamaBaseUrl,

        @Size(max = 128)
        String embeddingModel,

        @Size(max = 512)
        String confluenceBaseUrl,

        @Size(max = 512)
        String confluenceTarget,

        @Size(max = 64)
        String vectorStore,

        @Size(max = 256)
        String chromaHost,

        Integer chromaPort,

        @Size(max = 128)
        String chromaCollectionName,

        Boolean verifySsl,

        /** Empty or masked value keeps the existing token. */
        String pat
) {
    public ConfigRequest {
        if (confluenceBaseUrl != null) {
            confluenceBaseUrl = confluenceBaseUrl.strip().replaceAll("/+$", "");
        }
        if (confluenceTarget != null) {
            confluenceTarget = confluenceTarget.strip();
        }
    }
}
