package com.confluence.ingestor.service;

import com.confluence.ingestor.api.dto.IngestionRequest;
import com.confluence.ingestor.config.IngestorProperties;
import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.model.PageManifestEntry;
import com.confluence.ingestor.service.ChunkService.ChunkResult;
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
 * Background batch chunking of manifest pages that already have Markdown on disk.
 */
@Service
public class ChunkBatchService {

    private static final Logger log = LoggerFactory.getLogger(ChunkBatchService.class);

    private final IngestorProperties properties;
    private final ManifestService manifestService;
    private final ChunkService chunkService;
    private final VectorIngestBatchService vectorIngestBatchService;
    private final IngestionJobCoordinator jobCoordinator;
    private final BatchProgressService batchProgressService;
    private final PageIngestionStateService pageIngestionStateService;

    public ChunkBatchService(
            IngestorProperties properties,
            ManifestService manifestService,
            ChunkService chunkService,
            VectorIngestBatchService vectorIngestBatchService,
            IngestionJobCoordinator jobCoordinator,
            BatchProgressService batchProgressService,
            PageIngestionStateService pageIngestionStateService) {
        this.properties = properties;
        this.manifestService = manifestService;
        this.chunkService = chunkService;
        this.vectorIngestBatchService = vectorIngestBatchService;
        this.jobCoordinator = jobCoordinator;
        this.batchProgressService = batchProgressService;
        this.pageIngestionStateService = pageIngestionStateService;
    }

    @Async("ingestionTaskExecutor")
    @Observed(name = "ingestion.chunk.batch")
    public void runChunkBatchAsync(IngestionRequest request) {
        String parentPageId = request.parentPageId();
        int batchSize = request.resolvedBatchSize(properties.defaultBatchSize());
        int concurrency = request.resolvedConcurrency(properties.defaultConcurrency());
        AtomicInteger chunkedThisRun = new AtomicInteger();
        AtomicInteger failedThisRun = new AtomicInteger();
        BatchProgressService.BatchProgressTracker progress =
                batchProgressService.newTracker(parentPageId, BatchPhase.CHUNK);

        try {
            if (!manifestService.manifestExists(parentPageId)) {
                log.warn("Chunk batch skipped — no manifest for parentPageId={}", parentPageId);
                return;
            }

            int totalPending = countPendingForChunking(parentPageId);
            progress.bootstrapRunning(totalPending);

            log.info(
                    "Background chunk batch started for parentPageId={} batchSize={} concurrency={} pending={}",
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
                processBatch(parentPageId, pending, concurrency, chunkedThisRun, failedThisRun);
                int processed = chunkedThisRun.get() + failedThisRun.get();
                progress.onBatchTick(
                        processed,
                        failedThisRun.get(),
                        Math.max(0, totalPending - processed),
                        currentPageId);
            }

            progress.writeCompleted(chunkedThisRun.get(), failedThisRun.get());
            pageIngestionStateService.mergeAllIntoManifest(parentPageId);
            log.info(
                    "Background chunk batch finished for parentPageId={} chunkedThisRun={} failedThisRun={}",
                    parentPageId,
                    chunkedThisRun.get(),
                    failedThisRun.get());
            scheduleChainedVectorIngest(request, parentPageId);
        } catch (IOException ex) {
            progress.writeFailed(ex.getMessage());
            log.warn("Chunk batch I/O error for parentPageId={}: {}", parentPageId, ex.getMessage());
        } catch (Exception ex) {
            progress.writeFailed(ex.getMessage());
            log.error("Chunk batch error for parentPageId={}", parentPageId, ex);
        } finally {
            jobCoordinator.releaseChunkBatch(parentPageId);
        }
    }

    private List<PageManifestEntry> loadPendingBatch(String parentPageId, int batchSize) throws IOException {
        PageManifest manifest = manifestService.loadManifest(parentPageId);
        List<PageManifestEntry> pending = new ArrayList<>();
        if (manifest.getPages() == null) {
            return pending;
        }
        for (PageManifestEntry entry : manifest.getPages()) {
            if (manifestService.isPendingForChunking(entry)) {
                pending.add(entry);
                if (pending.size() >= batchSize) {
                    break;
                }
            }
        }
        return pending;
    }

    private int countPendingForChunking(String parentPageId) throws IOException {
        PageManifest manifest = manifestService.loadManifest(parentPageId);
        if (manifest.getPages() == null) {
            return 0;
        }
        int count = 0;
        for (PageManifestEntry entry : manifest.getPages()) {
            if (manifestService.isPendingForChunking(entry)) {
                count++;
            }
        }
        return count;
    }

    private void processBatch(
            String parentPageId,
            List<PageManifestEntry> batch,
            int concurrency,
            AtomicInteger chunkedThisRun,
            AtomicInteger failedThisRun) {
        Semaphore semaphore = new Semaphore(Math.max(1, concurrency));
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<ChunkResult>> futures = new ArrayList<>();
            for (PageManifestEntry entry : batch) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                semaphore.acquire();
                                return chunkService.chunkPage(parentPageId, entry);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                return ChunkResult.failure(entry.getPageId(), "Interrupted");
                            } finally {
                                semaphore.release();
                            }
                        },
                        executor));
            }
            for (CompletableFuture<ChunkResult> future : futures) {
                ChunkResult result = future.join();
                if (result.success()) {
                    chunkedThisRun.incrementAndGet();
                } else {
                    failedThisRun.incrementAndGet();
                }
            }
        }
    }

    private void scheduleChainedVectorIngest(IngestionRequest request, String parentPageId) {
        if (!request.shouldIngestVectors()) {
            return;
        }
        if (jobCoordinator.tryAcquireVectorIngest(parentPageId)) {
            vectorIngestBatchService.runVectorIngestBatchAsync(request);
            log.info("Chained vector ingest scheduled for parentPageId={}", parentPageId);
        } else {
            log.warn(
                    "Skipped chained vector ingest for parentPageId={} because another vector ingest is active",
                    parentPageId);
        }
    }
}
