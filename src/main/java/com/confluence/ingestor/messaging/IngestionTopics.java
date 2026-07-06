package com.confluence.ingestor.messaging;

/**
 * Kafka topic names for ingestion pipeline stages.
 */
public final class IngestionTopics {

    public static final String MANIFEST_CRAWL = "ingestion.manifest.crawl";
    public static final String PAGE_TRANSFORM = "ingestion.page.transform";
    public static final String CHUNK = "ingestion.page.chunk";
    public static final String VECTOR_INGEST = "ingestion.page.vector";

    public static final String MANIFEST_CRAWL_DLQ = "ingestion.manifest.crawl.dlq";
    public static final String PAGE_TRANSFORM_DLQ = "ingestion.page.transform.dlq";
    public static final String CHUNK_DLQ = "ingestion.page.chunk.dlq";
    public static final String VECTOR_INGEST_DLQ = "ingestion.page.vector.dlq";

    public static String topicFor(IngestionJobType jobType) {
        return switch (jobType) {
            case MANIFEST_CRAWL -> MANIFEST_CRAWL;
            case PAGE_TRANSFORM -> PAGE_TRANSFORM;
            case CHUNK -> CHUNK;
            case VECTOR_INGEST -> VECTOR_INGEST;
        };
    }

    public static String dlqFor(IngestionJobType jobType) {
        return switch (jobType) {
            case MANIFEST_CRAWL -> MANIFEST_CRAWL_DLQ;
            case PAGE_TRANSFORM -> PAGE_TRANSFORM_DLQ;
            case CHUNK -> CHUNK_DLQ;
            case VECTOR_INGEST -> VECTOR_INGEST_DLQ;
        };
    }

    private IngestionTopics() {
    }
}
