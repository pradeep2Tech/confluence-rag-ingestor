package com.confluence.ingestor.service;

/**
 * In-memory active background job for a {@code parentPageId}.
 */
public enum IngestionActiveJob {
    MANIFEST_CRAWL,
    PAGE_TRANSFORM,
    CHUNK,
    VECTOR_INGEST
}
