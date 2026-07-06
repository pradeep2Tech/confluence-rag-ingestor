package com.confluence.ingestor.api.dto;

/**
 * High-level ingestion job status (mirrors Python POC {@code ExportStatus}).
 */
public enum IngestionJobStatus {
    SUCCESS,
    ERROR,
    ACCEPTED,
    ALREADY_RUNNING
}
