package com.confluence.ingestor.service;

import com.confluence.ingestor.api.dto.IngestionRequest;
import com.confluence.ingestor.confluence.ConfluenceClient;
import com.confluence.ingestor.confluence.ConfluenceClientFactory;
import com.confluence.ingestor.config.IngestorProperties;
import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.model.PageManifestEntry;
import com.confluence.ingestor.service.PageTransformService.TransformResult;
import com.confluence.ingestor.storage.BatchPhase;
import com.confluence.ingestor.storage.BatchProgressService;
import com.confluence.ingestor.storage.PageIngestionStateService;
import com.confluence.ingestor.storage.ManifestService;
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
 * Background batch extraction of pending manifest pages to Markdown.
 */
@Service
public class PageTransformBatchService {

    private static final Logger log = LoggerFactory.getLogger(PageTransformBatchService.class);

    private final IngestorProperties properties;
    private final ConfluenceClientFactory clientFactory;
    private final ManifestService manifestService;
    private final PageTransformService pageTransformService;
    private final IngestionJobCoordinator jobCoordinator;
    private final ChunkBatchService chunkBatchService;
    private final BatchProgressService batchProgressService;
    private final PageIngestionStateService pageIngestionStateService;

    public PageTransformBatchService(
            IngestorProperties properties,
            ConfluenceClientFactory clientFactory,
            ManifestService manifestService,
            PageTransformService pageTransformService,
            IngestionJobCoordinator jobCoordinator,
            ChunkBatchService chunkBatchService,
            BatchProgressService batchProgressService,
            PageIngestionStateService pageIngestionStateService) {
        this.properties = properties;
        this.clientFactory = clientFactory;
        this.manifestService = manifestService;
        this.pageTransformService = pageTransformService;
        this.jobCoordinator = jobCoordinator;
        this.chunkBatchService = chunkBatchService;
        this.batchProgressService = batchProgressService;
        this.pageIngestionStateService = pageIngestionStateService;
    }

    @Async("ingestionTaskExecutor")
    @Observed(name = "ingestion.page.transform.batch")
    public void runPageTransformBatchAsync(IngestionRequest request) {
        String parentPageId = request.parentPageId();
        int batchSize = request.resolvedBatchSize(properties.defaultBatchSize());
        int concurrency = request.resolvedConcurrency(properties.defaultConcurrency());
        AtomicInteger ingestedThisRun = new AtomicInteger();
        AtomicInteger failedThisRun = new AtomicInteger();
        BatchProgressService.BatchProgressTracker progress =
                batchProgressService.newTracker(parentPageId, BatchPhase.PAGE_TRANSFORM);
        boolean completed = false;

        try {
            if (!manifestService.manifestExists(parentPageId)) {
                log.warn("Page transform skipped — no manifest for parentPageId={}", parentPageId);
                return;
            }

            int totalPending = countPendingForTransform(parentPageId);
            progress.bootstrapRunning(totalPending);

            int timeout = request.resolvedRequestTimeoutSeconds(properties.defaultRequestTimeoutSeconds());
            ConfluenceClient client = clientFactory.create(request.baseUrl(), request.pat(), timeout);

            log.info(
                    "Background page transform started for parentPageId={} batchSize={} concurrency={} pending={}",
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
                processBatch(parentPageId, pending, client, concurrency, ingestedThisRun, failedThisRun);
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
                    "Background page transform finished for parentPageId={} ingestedThisRun={} failedThisRun={}",
                    parentPageId,
                    ingestedThisRun.get(),
                    failedThisRun.get());
            completed = true;
        } catch (IOException ex) {
            progress.writeFailed(ex.getMessage());
            log.warn("Page transform I/O error for parentPageId={}: {}", parentPageId, ex.getMessage());
        } catch (Exception ex) {
            progress.writeFailed(ex.getMessage());
            log.error("Page transform error for parentPageId={}", parentPageId, ex);
        } finally {
            jobCoordinator.releasePageTransform(parentPageId);
        }

        if (completed) {
            scheduleChainedChunkBatch(request, parentPageId);
        }
    }

    private List<PageManifestEntry> loadPendingBatch(String parentPageId, int batchSize) throws IOException {
        PageManifest manifest = manifestService.loadManifest(parentPageId);
        List<PageManifestEntry> pending = new ArrayList<>();
        if (manifest.getPages() == null) {
            return pending;
        }
        for (PageManifestEntry entry : manifest.getPages()) {
            if (manifestService.isPendingForIngestion(entry)) {
                pending.add(entry);
                if (pending.size() >= batchSize) {
                    break;
                }
            }
        }
        return pending;
    }

    private int countPendingForTransform(String parentPageId) throws IOException {
        PageManifest manifest = manifestService.loadManifest(parentPageId);
        if (manifest.getPages() == null) {
            return 0;
        }
        int count = 0;
        for (PageManifestEntry entry : manifest.getPages()) {
            if (manifestService.isPendingForIngestion(entry)) {
                count++;
            }
        }
        return count;
    }

    private void processBatch(
            String parentPageId,
            List<PageManifestEntry> batch,
            ConfluenceClient client,
            int concurrency,
            AtomicInteger ingestedThisRun,
            AtomicInteger failedThisRun) {
        Semaphore semaphore = new Semaphore(Math.max(1, concurrency));
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<TransformResult>> futures = new ArrayList<>();
            for (PageManifestEntry entry : batch) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                semaphore.acquire();
                                return pageTransformService.transformPage(parentPageId, entry, client);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                return TransformResult.failure(entry.getPageId(), "Interrupted");
                            } finally {
                                semaphore.release();
                            }
                        },
                        executor));
            }
            for (CompletableFuture<TransformResult> future : futures) {
                TransformResult result = future.join();
                if (result.success()) {
                    ingestedThisRun.incrementAndGet();
                } else {
                    failedThisRun.incrementAndGet();
                }
            }
        }
    }

    private void scheduleChainedChunkBatch(IngestionRequest request, String parentPageId) {
        if (!request.shouldChunkMarkdown()) {
            return;
        }
        if (jobCoordinator.tryAcquireChunkBatch(parentPageId)) {
            chunkBatchService.runChunkBatchAsync(request);
            log.info("Chained chunk batch scheduled for parentPageId={}", parentPageId);
        } else {
            log.warn(
                    "Skipped chained chunk batch for parentPageId={} — could not acquire chunk batch lock",
                    parentPageId);
        }
    }
}
