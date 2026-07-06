package com.confluence.ingestor.port;

/**
 * Aggregated manifest counters for status and API responses.
 */
public record ManifestSummary(int totalPages, int ingestedCount, int pendingCount, int failedCount) {
}
