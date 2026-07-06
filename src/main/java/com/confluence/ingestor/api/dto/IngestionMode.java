package com.confluence.ingestor.api.dto;

/**
 * Ingestion mode — Phase 1 only defines {@link #MANIFEST_INIT}; later phases add crawl/transform/chunk modes.
 */
public enum IngestionMode {
    MANIFEST_INIT,
    /** Phase 2: crawl Confluence tree and build manifest. */
    MANIFEST_CRAWL,
    /** Phase 3+: transform pages to Markdown. */
    PAGE_TRANSFORM,
    /** Phase 7+: chunk Markdown for RAG. */
    CHUNK,
    /** Phase 8+: embed and load ChromaDB. */
    VECTOR_INGEST
}
