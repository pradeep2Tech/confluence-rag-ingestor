package com.confluence.ingestor.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Throttled writes to {@code data/{parentPageId}/batch-progress.json} during batch jobs.
 * Mirrors Python POC {@code batch-progress.json} for transform/chunk/vector stages.
 */
@Service
public class BatchProgressService {

    private static final Logger log = LoggerFactory.getLogger(BatchProgressService.class);

    private final FileStorageService fileStorageService;

    public BatchProgressService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public BatchProgressTracker newTracker(String parentPageId, BatchPhase phase) {
        return new BatchProgressTracker(parentPageId, phase, Instant.now());
    }

    public final class BatchProgressTracker {

        private final String parentPageId;
        private final BatchPhase phase;
        private final Instant startedAt;
        private int processedCount;
        private int failedCount;
        private int lastWrittenProcessed = -1;

        private BatchProgressTracker(String parentPageId, BatchPhase phase, Instant startedAt) {
            this.parentPageId = parentPageId;
            this.phase = phase;
            this.startedAt = startedAt;
        }

        public void bootstrapRunning(int totalPending) {
            writeProgress("RUNNING", 0, 0, totalPending, null, null);
        }

        public void onBatchTick(int processedCount, int failedCount, int totalPending, String currentPageId) {
            this.processedCount = processedCount;
            this.failedCount = failedCount;
            if (processedCount == lastWrittenProcessed) {
                return;
            }
            lastWrittenProcessed = processedCount;
            writeProgress("RUNNING", processedCount, failedCount, totalPending, currentPageId, null);
        }

        public void writeCompleted(int processedCount, int failedCount) {
            this.processedCount = processedCount;
            this.failedCount = failedCount;
            writeProgress("COMPLETED", processedCount, failedCount, 0, null, null);
        }

        public void writeFailed(String message) {
            String error = message != null && message.length() > 2000 ? message.substring(0, 2000) : message;
            writeProgress("FAILED", processedCount, failedCount, 0, null, error);
        }

        private void writeProgress(
                String status,
                int processed,
                int failed,
                int totalPending,
                String currentPageId,
                String error) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("parentPageId", parentPageId);
            payload.put("phase", phase.name());
            payload.put("status", status);
            payload.put("processedCount", processed);
            payload.put("failedCount", failed);
            payload.put("totalPending", totalPending);
            payload.put("currentPageId", currentPageId);
            payload.put("startedAt", startedAt.toString());
            payload.put("error", error);
            try {
                fileStorageService.writeJsonAtomic(
                        fileStorageService.batchProgressPath(parentPageId), payload);
            } catch (IOException ex) {
                log.warn(
                        "Failed to write batch progress for parentPageId={} phase={}: {}",
                        parentPageId,
                        phase,
                        ex.getMessage());
            }
        }
    }
}
