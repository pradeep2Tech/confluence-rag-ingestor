package com.confluence.ingestor.api.dto;

/**
 * Request body for {@code POST /api/ingest} — uses saved configuration when fields are omitted.
 */
public record UiIngestRequest(
        Boolean forceRebuildManifest,
        Boolean extractMarkdown,
        Boolean chunkMarkdown,
        Boolean ingestVectors,
        Integer batchSize,
        Integer concurrency
) {
    public boolean shouldForceRebuildManifest() {
        return forceRebuildManifest == null || Boolean.TRUE.equals(forceRebuildManifest);
    }

    public boolean shouldExtractMarkdown() {
        return extractMarkdown == null || Boolean.TRUE.equals(extractMarkdown);
    }

    public boolean shouldChunkMarkdown() {
        return chunkMarkdown == null || Boolean.TRUE.equals(chunkMarkdown);
    }

    public boolean shouldIngestVectors() {
        return ingestVectors == null || Boolean.TRUE.equals(ingestVectors);
    }
}
