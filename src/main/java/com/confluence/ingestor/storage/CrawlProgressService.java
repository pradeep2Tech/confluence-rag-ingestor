package com.confluence.ingestor.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Throttled writes to {@code data/{parentPageId}/crawl-progress.json} during manifest crawl.
 * Mirrors Python {@code crawl_progress_service.py}.
 */
@Service
public class CrawlProgressService {

    private static final Logger log = LoggerFactory.getLogger(CrawlProgressService.class);

    private final FileStorageService fileStorageService;

    public CrawlProgressService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public CrawlProgressTracker newTracker(String parentPageId) {
        return new CrawlProgressTracker(parentPageId, Instant.now());
    }

    public final class CrawlProgressTracker {

        private final String parentPageId;
        private final Instant startedAt;
        private int lastDiscoveredQuarter = -1;
        private int lastProcessedQuarter = -1;
        private int lastProcessedLogHundred = -1;
        private int lastProcessed;
        private int lastDiscovered;

        private CrawlProgressTracker(String parentPageId, Instant startedAt) {
            this.parentPageId = parentPageId;
            this.startedAt = startedAt;
        }

        public void bootstrapRunning(String currentPageId, String currentTitle) {
            lastDiscovered = 1;
            writeProgress("RUNNING", 0, 1, 1, currentPageId, currentTitle, null);
        }

        public void onCrawlTick(
                int processedPages,
                int totalPagesDiscovered,
                int queueSize,
                String currentPageId,
                String currentTitle) {
            lastProcessed = processedPages;
            lastDiscovered = totalPagesDiscovered;

            int discoveredQuarter = totalPagesDiscovered / 25;
            int processedQuarter = processedPages / 25;
            boolean writeFile = discoveredQuarter > lastDiscoveredQuarter || processedQuarter > lastProcessedQuarter;
            if (writeFile) {
                lastDiscoveredQuarter = discoveredQuarter;
                lastProcessedQuarter = processedQuarter;
                writeProgress(
                        "RUNNING",
                        processedPages,
                        totalPagesDiscovered,
                        queueSize,
                        currentPageId,
                        currentTitle,
                        null);
            }

            if (processedPages > 0) {
                int processedHundred = processedPages / 100;
                if (processedHundred > lastProcessedLogHundred) {
                    lastProcessedLogHundred = processedHundred;
                    log.info(
                            "Manifest crawl progress parentPageId={} processedPages={} "
                                    + "totalPagesDiscovered={} queueSize={}",
                            parentPageId,
                            processedPages,
                            totalPagesDiscovered,
                            queueSize);
                }
            }
        }

        public void writeCompleted(int totalPagesDiscovered) {
            writeProgress("COMPLETED", lastProcessed, totalPagesDiscovered, 0, null, null, null);
        }

        public void writeFailed(String message) {
            String error = message != null && message.length() > 2000 ? message.substring(0, 2000) : message;
            writeProgress("FAILED", lastProcessed, lastDiscovered, 0, null, null, error);
        }

        private void writeProgress(
                String status,
                int processedPages,
                int totalPagesDiscovered,
                int queueSize,
                String currentPageId,
                String currentTitle,
                String error) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("parentPageId", parentPageId);
            payload.put("status", status);
            payload.put("processedPages", processedPages);
            payload.put("totalPagesDiscovered", totalPagesDiscovered);
            payload.put("queueSize", queueSize);
            payload.put("currentPageId", currentPageId);
            payload.put("currentTitle", currentTitle);
            payload.put("startedAt", startedAt.toString());
            payload.put("error", error);
            try {
                fileStorageService.writeJsonAtomic(
                        fileStorageService.crawlProgressPath(parentPageId), payload);
            } catch (IOException ex) {
                log.warn(
                        "Failed to write crawl progress for parentPageId={}: {}",
                        parentPageId,
                        ex.getMessage());
            }
        }
    }
}
