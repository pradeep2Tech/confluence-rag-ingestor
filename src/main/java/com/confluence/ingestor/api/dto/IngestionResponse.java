package com.confluence.ingestor.api.dto;

/**
 * Response for {@code POST /api/confluence/ingest}.
 */
public record IngestionResponse(
        IngestionJobStatus status,
        IngestionMode mode,
        String parentPageId,
        String manifestPath,
        int totalPages,
        int ingestedCount,
        int pendingCount,
        int failedCount,
        int ingestedThisRun,
        int failedThisRun,
        String message,
        String errorDetail
) {
    public static IngestionResponse success(
            IngestionMode mode,
            String parentPageId,
            String manifestPath,
            int totalPages,
            int ingestedCount,
            int pendingCount,
            int failedCount,
            String message) {
        return new IngestionResponse(
                IngestionJobStatus.SUCCESS,
                mode,
                parentPageId,
                manifestPath,
                totalPages,
                ingestedCount,
                pendingCount,
                failedCount,
                0,
                0,
                message,
                null);
    }

    public static IngestionResponse accepted(
            IngestionMode mode,
            String parentPageId,
            String manifestPath,
            int totalPages,
            int ingestedCount,
            int pendingCount,
            int failedCount,
            String message) {
        return new IngestionResponse(
                IngestionJobStatus.ACCEPTED,
                mode,
                parentPageId,
                manifestPath,
                totalPages,
                ingestedCount,
                pendingCount,
                failedCount,
                0,
                0,
                message,
                null);
    }

    public static IngestionResponse alreadyRunning(
            IngestionMode mode,
            String parentPageId,
            String manifestPath,
            int totalPages,
            int ingestedCount,
            int pendingCount,
            int failedCount,
            String message) {
        return new IngestionResponse(
                IngestionJobStatus.ALREADY_RUNNING,
                mode,
                parentPageId,
                manifestPath,
                totalPages,
                ingestedCount,
                pendingCount,
                failedCount,
                0,
                0,
                message,
                null);
    }

    public static IngestionResponse error(
            IngestionMode mode,
            String parentPageId,
            String manifestPath,
            String message,
            String errorDetail) {
        return new IngestionResponse(
                IngestionJobStatus.ERROR,
                mode,
                parentPageId,
                manifestPath,
                0,
                0,
                0,
                0,
                0,
                0,
                message,
                errorDetail);
    }
}
