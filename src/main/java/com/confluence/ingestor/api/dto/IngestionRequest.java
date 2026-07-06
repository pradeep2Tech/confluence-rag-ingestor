package com.confluence.ingestor.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/confluence/ingest}.
 * <p>
 * PAT is required for API contract parity with the Python POC; Phase 1 validates presence only
 * and does not call Confluence.
 */
public record IngestionRequest(
        @NotBlank(message = "baseUrl is required")
        String baseUrl,

        @NotBlank(message = "parentPageId is required")
        @Size(max = 64, message = "parentPageId must be at most 64 characters")
        String parentPageId,

        @NotBlank(message = "pat is required")
        String pat,

        Boolean forceRebuildManifest,

        Boolean extractMarkdown,

        Boolean chunkMarkdown,

        Boolean ingestVectors,

        @Min(1) @Max(5000)
        Integer batchSize,

        @Min(1) @Max(32)
        Integer concurrency,

        @Min(10) @Max(900)
        Integer requestTimeoutSeconds
) {
    public IngestionRequest {
        if (baseUrl != null) {
            baseUrl = baseUrl.strip().replaceAll("/+$", "");
        }
        if (parentPageId != null) {
            parentPageId = parentPageId.strip();
        }
        // Never log pat — compact constructor only normalizes baseUrl/parentPageId.
    }

    public int resolvedBatchSize(int defaultBatchSize) {
        return batchSize != null ? batchSize : defaultBatchSize;
    }

    public int resolvedConcurrency(int defaultConcurrency) {
        return concurrency != null ? concurrency : defaultConcurrency;
    }

    public int resolvedRequestTimeoutSeconds(int defaultTimeout) {
        return requestTimeoutSeconds != null ? requestTimeoutSeconds : defaultTimeout;
    }

    public boolean shouldForceRebuildManifest() {
        return Boolean.TRUE.equals(forceRebuildManifest);
    }

    public boolean shouldExtractMarkdown() {
        return Boolean.TRUE.equals(extractMarkdown);
    }

    public boolean shouldChunkMarkdown() {
        return Boolean.TRUE.equals(chunkMarkdown);
    }

    public boolean shouldIngestVectors() {
        return Boolean.TRUE.equals(ingestVectors);
    }
}
