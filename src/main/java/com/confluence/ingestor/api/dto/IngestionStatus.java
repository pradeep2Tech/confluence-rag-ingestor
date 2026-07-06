package com.confluence.ingestor.api.dto;

import java.util.Map;

/**
 * Disk-backed status for {@code GET /api/confluence/ingest/status/{parentPageId}}.
 * No Confluence auth required — reads local files only (same as Python status endpoint).
 */
public record IngestionStatus(
        String parentPageId,
        String manifestPath,
        String crawlProgressPath,
        String batchProgressPath,
        boolean manifestExists,
        Map<String, Object> crawlProgress,
        Map<String, Object> batchProgress,
        String activeJob,
        int totalPages,
        int ingestedCount,
        int pendingCount,
        int failedCount,
        String message
) {
}
