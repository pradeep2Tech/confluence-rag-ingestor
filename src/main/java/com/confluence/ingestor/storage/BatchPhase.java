package com.confluence.ingestor.storage;

/**
 * Background batch job phase written to {@code batch-progress.json}.
 */
public enum BatchPhase {
    PAGE_TRANSFORM,
    CHUNK,
    VECTOR_INGEST
}
