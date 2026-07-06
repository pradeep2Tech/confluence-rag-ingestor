package com.confluence.ingestor.messaging;

/**
 * Pipeline stage identifiers — map 1:1 to Kafka topics in Phase 12.
 */
public enum IngestionJobType {
    MANIFEST_CRAWL,
    PAGE_TRANSFORM,
    CHUNK,
    VECTOR_INGEST
}
