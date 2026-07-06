package com.confluence.ingestor.service;

import com.confluence.ingestor.api.dto.IngestionRequest;
import com.confluence.ingestor.config.IngestorProperties;
import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.model.PageManifestEntry;
import com.confluence.ingestor.service.VectorIngestService.VectorIngestResult;
import com.confluence.ingestor.storage.BatchPhase;
import com.confluence.ingestor.storage.BatchProgressService;
import com.confluence.ingestor.storage.ManifestService;
import com.confluence.ingestor.storage.PageIngestionStateService;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background batch ingestion of chunked pages into ChromaDB via Spring AI.
 */
@Service
public class VectorIngestBatchService {

    private static final Logger log = LoggerFactory.getLogger(VectorIngestBatchService.class);

    private final IngestorProperties properties;
    private final ManifestService manifestService;
    private final VectorIngestService vectorIngestService;
    private final IngestionJobCoordinator jobCoordinator;
    private final BatchProgressService batchProgressService;
    private final PageIngestionStateService pageIngestionStateService;

    public VectorIngestBatchService(
            IngestorProperties properties,
            ManifestService manifestService,
            VectorIngestService vectorIngestService,
            IngestionJobCoordinator jobCoordinator,
            BatchProgressService batchProgressService,
            PageIngestionStateService pageIngestionStateService) {
        this.properties = properties;
        this.manifestService = manifestService;
        this.vectorIngestService = vectorIngestService;
        this.jobCoordinator = jobCoordinator;
        this.batchProgressService = batchProgressService;
        this.pageIngestionStateService = pageIngestionStateService;
    }

    @Async("ingestionTaskExecutor")
    @Observed(name = "ingestion.vector.batch")
    public void runVectorIngestBatchAsync(IngestionRequest request) {
        String parentPageId = request.parentPageId();
        int batchSize = request.resolvedBatchSize(properties.defaultBatchSize());
        int concurrency = request.resolvedConcurrency(properties.defaultConcurrency());
        AtomicInteger ingestedThisRun = new AtomicInteger();
        AtomicInteger failedThisRun = new AtomicInteger();
        BatchProgressService.BatchProgressTracker progress =
                batchProgressService.newTracker(parentPageId, BatchPhase.VECTOR_INGEST);

        try {
            if (!manifestService.manifestExists(parentPageId)) {
                log.warn("Vector ingest batch skipped — no manifest for parentPageId={}", parentPageId);
                return;
            }

            int totalPending = countPendingForVectorIngest(parentPageId);
            progress.bootstrapRunning(totalPending);

            log.info(
                    "Background vector ingest started for parentPageId={} batchSize={} concurrency={} pending={}",
                    parentPageId,
                    batchSize,
                    concurrency,
                    totalPending);

            while (true) {
                List<PageManifestEntry> pending = loadPendingBatch(parentPageId, batchSize);
                if (pending.isEmpty()) {
                    break;
                }
                String currentPageId = pending.get(pending.size() - 1).getPageId();
                processBatch(parentPageId, pending, concurrency, ingestedThisRun, failedThisRun);
                int processed = ingestedThisRun.get() + failedThisRun.get();
                progress.onBatchTick(
                        processed,
                        failedThisRun.get(),
                        Math.max(0, totalPending - processed),
                        currentPageId);
            }

            progress.writeCompleted(ingestedThisRun.get(), failedThisRun.get());
            pageIngestionStateService.mergeAllIntoManifest(parentPageId);
            log.info(
                    "Background vector ingest finished for parentPageId={} ingestedThisRun={} failedThisRun={}",
                    parentPageId,
                    ingestedThisRun.get(),
                    failedThisRun.get());
        } catch (IOException ex) {
            progress.writeFailed(ex.getMessage());
            log.warn("Vector ingest batch I/O error for parentPageId={}: {}", parentPageId, ex.getMessage());
        } catch (Exception ex) {
            progress.writeFailed(ex.getMessage());
            log.error("Vector ingest batch error for parentPageId={}", parentPageId, ex);
        } finally {
            jobCoordinator.releaseVectorIngest(parentPageId);
        }
    }

    private List<PageManifestEntry> loadPendingBatch(String parentPageId, int batchSize) throws IOException {
        PageManifest manifest = manifestService.loadManifest(parentPageId);
        List<PageManifestEntry> pending = new ArrayList<>();
        if (manifest.getPages() == null) {
            return pending;
        }
        for (PageManifestEntry entry : manifest.getPages()) {
            if (manifestService.isPendingForVectorIngest(entry)) {
                pending.add(entry);
                if (pending.size() >= batchSize) {
                    break;
                }
            }
        }
        return pending;
    }

    private int countPendingForVectorIngest(String parentPageId) throws IOException {
        PageManifest manifest = manifestService.loadManifest(parentPageId);
        if (manifest.getPages() == null) {
            return 0;
        }
        int count = 0;
        for (PageManifestEntry entry : manifest.getPages()) {
            if (manifestService.isPendingForVectorIngest(entry)) {
                count++;
            }
        }
        return count;
    }

    private void processBatch(
            String parentPageId,
            List<PageManifestEntry> batch,
            int concurrency,
            AtomicInteger ingestedThisRun,
            AtomicInteger failedThisRun) {
        Semaphore semaphore = new Semaphore(Math.max(1, concurrency));
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<VectorIngestResult>> futures = new ArrayList<>();
            for (PageManifestEntry entry : batch) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                semaphore.acquire();
                                return vectorIngestService.ingestPage(parentPageId, entry);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                return VectorIngestResult.failure(entry.getPageId(), "Interrupted");
                            } finally {
                                semaphore.release();
                            }
                        },
                        executor));
            }
            for (CompletableFuture<VectorIngestResult> future : futures) {
                VectorIngestResult result = future.join();
                if (result.success()) {
                    ingestedThisRun.incrementAndGet();
                } else {
                    failedThisRun.incrementAndGet();
                }
            }
        }
    }
}
