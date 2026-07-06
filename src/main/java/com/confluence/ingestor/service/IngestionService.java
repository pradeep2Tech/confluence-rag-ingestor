package com.confluence.ingestor.service;

import com.confluence.ingestor.api.dto.IngestionMode;
import com.confluence.ingestor.api.dto.IngestionRequest;
import com.confluence.ingestor.api.dto.IngestionResponse;
import com.confluence.ingestor.api.dto.IngestionStatus;
import com.confluence.ingestor.config.IngestorProperties;
import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.model.PageManifestEntry;
import com.confluence.ingestor.storage.FileStorageService;
import com.confluence.ingestor.storage.ManifestService;
import com.confluence.ingestor.port.JobCoordinatorPort;
import com.confluence.ingestor.port.JobPublisher;
import com.confluence.ingestor.port.ManifestSummary;
import com.confluence.ingestor.messaging.IngestionJobType;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

/**
 * Orchestrates ingestion requests. Phase 3 adds Confluence HTML to Markdown extraction.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final IngestorProperties properties;
    private final FileStorageService fileStorageService;
    private final ManifestService manifestService;
    private final JobPublisher jobPublisher;
    private final JobCoordinatorPort jobCoordinator;

    public IngestionService(
            IngestorProperties properties,
            FileStorageService fileStorageService,
            ManifestService manifestService,
            JobPublisher jobPublisher,
            JobCoordinatorPort jobCoordinator) {
        this.properties = properties;
        this.fileStorageService = fileStorageService;
        this.manifestService = manifestService;
        this.jobPublisher = jobPublisher;
        this.jobCoordinator = jobCoordinator;
    }

    @Observed(name = "ingestion.start")
    public IngestionResponse startIngestion(IngestionRequest request) {
        String parentPageId = request.parentPageId();
        String manifestPath = manifestService.manifestDisplayPath(parentPageId);

        log.info(
                "Starting ingestion parentPageId={} forceRebuild={} extractMarkdown={} chunkMarkdown={} ingestVectors={}",
                parentPageId,
                request.shouldForceRebuildManifest(),
                request.shouldExtractMarkdown(),
                request.shouldChunkMarkdown(),
                request.shouldIngestVectors());

        if (request.pat().isBlank()) {
            return IngestionResponse.error(
                    IngestionMode.MANIFEST_INIT,
                    parentPageId,
                    manifestPath,
                    "Personal access token is required",
                    "pat must not be blank");
        }

        try {
            fileStorageService.ensureParentDataLayout(parentPageId);
            logResolvedRequestOptions(request);

            if (request.shouldForceRebuildManifest()) {
                return startManifestCrawl(request, parentPageId, manifestPath);
            }

            if (request.shouldExtractMarkdown()) {
                return startPageTransform(request, parentPageId, manifestPath);
            }

            if (request.shouldChunkMarkdown()) {
                return startChunkBatch(request, parentPageId, manifestPath);
            }

            if (request.shouldIngestVectors()) {
                return startVectorIngest(request, parentPageId, manifestPath);
            }

            boolean created = manifestService.createEmptyManifestIfMissing(request.baseUrl(), parentPageId);

            if (created) {
                return IngestionResponse.success(
                        IngestionMode.MANIFEST_INIT,
                        parentPageId,
                        manifestPath,
                        0,
                        0,
                        0,
                        0,
                        "Initialized data directory and empty manifest.json. "
                                + "Set forceRebuildManifest=true to crawl Confluence, "
                                + "extractMarkdown=true to transform pages, "
                                + "chunkMarkdown=true to chunk extracted pages, "
                                + "or ingestVectors=true to load chunks into ChromaDB.");
            }

            ManifestSummary summary = readSummary(parentPageId);
            return IngestionResponse.success(
                    IngestionMode.MANIFEST_INIT,
                    parentPageId,
                    manifestPath,
                    summary.totalPages(),
                    summary.ingestedCount(),
                    summary.pendingCount(),
                    summary.failedCount(),
                    "Manifest already exists. Set forceRebuildManifest=true to rebuild, "
                            + "extractMarkdown=true to transform pending pages, "
                            + "chunkMarkdown=true to chunk extracted pages, "
                            + "or ingestVectors=true to load chunks into ChromaDB.");
        } catch (IOException ex) {
            log.error("Failed to initialize ingestion for parentPageId={}: {}", parentPageId, ex.getMessage());
            return IngestionResponse.error(
                    IngestionMode.MANIFEST_INIT,
                    parentPageId,
                    manifestPath,
                    "Failed to initialize on-disk ingestion workspace",
                    ex.getMessage());
        }
    }

    private IngestionResponse startManifestCrawl(
            IngestionRequest request, String parentPageId, String manifestPath) throws IOException {
        boolean manifestExists = manifestService.manifestExists(parentPageId);

        if (!jobCoordinator.tryAcquireManifestBuild(parentPageId)) {
            ManifestSummary summary = manifestExists ? readSummary(parentPageId) : emptySummary();
            String crawlHint = fileStorageService.displayPath(fileStorageService.crawlProgressPath(parentPageId));
            return IngestionResponse.alreadyRunning(
                    IngestionMode.MANIFEST_CRAWL,
                    parentPageId,
                    manifestPath,
                    summary.totalPages(),
                    summary.ingestedCount(),
                    summary.pendingCount(),
                    summary.failedCount(),
                    "Manifest crawl already in progress for parentPageId="
                            + parentPageId
                            + ". Watch "
                            + crawlHint
                            + ", "
                            + manifestPath
                            + ", and logs/app.log.");
        }

        jobPublisher.publish(IngestionJobType.MANIFEST_CRAWL, request);
        ManifestSummary summary = manifestExists ? readSummary(parentPageId) : emptySummary();
        String crawlHint = fileStorageService.displayPath(fileStorageService.crawlProgressPath(parentPageId));
        String message = "Manifest crawl started in the background. Watch "
                + crawlHint
                + ", "
                + manifestPath
                + ", and logs/app.log.";
        if (request.shouldExtractMarkdown()) {
            message += " Markdown extraction will start automatically after the crawl completes.";
        }
        if (request.shouldChunkMarkdown()) {
            message += " Chunking will start automatically after Markdown extraction completes.";
        }
        if (request.shouldIngestVectors()) {
            message += " Vector ingest will start automatically after chunking completes.";
        }
        return IngestionResponse.accepted(
                IngestionMode.MANIFEST_CRAWL,
                parentPageId,
                manifestPath,
                summary.totalPages(),
                summary.ingestedCount(),
                summary.pendingCount(),
                summary.failedCount(),
                message);
    }

    private IngestionResponse startPageTransform(
            IngestionRequest request, String parentPageId, String manifestPath) throws IOException {
        if (!manifestService.manifestExists(parentPageId)) {
            return IngestionResponse.error(
                    IngestionMode.PAGE_TRANSFORM,
                    parentPageId,
                    manifestPath,
                    "Manifest not found",
                    "Run forceRebuildManifest=true before extractMarkdown=true");
        }

        ManifestSummary summary = readSummary(parentPageId);
        if (summary.pendingCount() == 0) {
            return IngestionResponse.success(
                    IngestionMode.PAGE_TRANSFORM,
                    parentPageId,
                    manifestPath,
                    summary.totalPages(),
                    summary.ingestedCount(),
                    summary.pendingCount(),
                    summary.failedCount(),
                    "No pending pages for Markdown extraction.");
        }

        if (!jobCoordinator.tryAcquirePageTransform(parentPageId)) {
            return IngestionResponse.alreadyRunning(
                    IngestionMode.PAGE_TRANSFORM,
                    parentPageId,
                    manifestPath,
                    summary.totalPages(),
                    summary.ingestedCount(),
                    summary.pendingCount(),
                    summary.failedCount(),
                    "Page transform already in progress for parentPageId="
                            + parentPageId
                            + ". Watch "
                            + manifestPath
                            + " and logs/app.log.");
        }

        jobPublisher.publish(IngestionJobType.PAGE_TRANSFORM, request);
        return IngestionResponse.accepted(
                IngestionMode.PAGE_TRANSFORM,
                parentPageId,
                manifestPath,
                summary.totalPages(),
                summary.ingestedCount(),
                summary.pendingCount(),
                summary.failedCount(),
                "Markdown extraction started in the background for pending manifest pages.");
    }

    private IngestionResponse startChunkBatch(
            IngestionRequest request, String parentPageId, String manifestPath) throws IOException {
        if (!manifestService.manifestExists(parentPageId)) {
            return IngestionResponse.error(
                    IngestionMode.CHUNK,
                    parentPageId,
                    manifestPath,
                    "Manifest not found",
                    "Run extractMarkdown=true before chunkMarkdown=true");
        }

        ManifestSummary summary = readSummary(parentPageId);
        int pendingChunks = countPendingChunks(parentPageId);
        if (pendingChunks == 0) {
            return IngestionResponse.success(
                    IngestionMode.CHUNK,
                    parentPageId,
                    manifestPath,
                    summary.totalPages(),
                    summary.ingestedCount(),
                    summary.pendingCount(),
                    summary.failedCount(),
                    "No extracted pages pending chunking.");
        }

        if (!jobCoordinator.tryAcquireChunkBatch(parentPageId)) {
            return IngestionResponse.alreadyRunning(
                    IngestionMode.CHUNK,
                    parentPageId,
                    manifestPath,
                    summary.totalPages(),
                    summary.ingestedCount(),
                    summary.pendingCount(),
                    summary.failedCount(),
                    "Chunk batch already in progress for parentPageId="
                            + parentPageId
                            + ". Watch "
                            + manifestPath
                            + " and logs/app.log.");
        }

        jobPublisher.publish(IngestionJobType.CHUNK, request);
        return IngestionResponse.accepted(
                IngestionMode.CHUNK,
                parentPageId,
                manifestPath,
                summary.totalPages(),
                summary.ingestedCount(),
                summary.pendingCount(),
                summary.failedCount(),
                "Chunk batch started in the background for extracted pages.");
    }

    private IngestionResponse startVectorIngest(
            IngestionRequest request, String parentPageId, String manifestPath) throws IOException {
        if (!manifestService.manifestExists(parentPageId)) {
            return IngestionResponse.error(
                    IngestionMode.VECTOR_INGEST,
                    parentPageId,
                    manifestPath,
                    "Manifest not found",
                    "Run chunkMarkdown=true before ingestVectors=true");
        }

        if (!properties.vectorIngestEnabled()) {
            return IngestionResponse.error(
                    IngestionMode.VECTOR_INGEST,
                    parentPageId,
                    manifestPath,
                    "Vector ingest disabled",
                    "Set confluence.ingestor.vector-ingest-enabled=true");
        }

        ManifestSummary summary = readSummary(parentPageId);
        int pendingVectors = countPendingVectorIngest(parentPageId);
        if (pendingVectors == 0) {
            return IngestionResponse.success(
                    IngestionMode.VECTOR_INGEST,
                    parentPageId,
                    manifestPath,
                    summary.totalPages(),
                    summary.ingestedCount(),
                    summary.pendingCount(),
                    summary.failedCount(),
                    "No chunked pages pending vector ingest.");
        }

        if (!jobCoordinator.tryAcquireVectorIngest(parentPageId)) {
            return IngestionResponse.alreadyRunning(
                    IngestionMode.VECTOR_INGEST,
                    parentPageId,
                    manifestPath,
                    summary.totalPages(),
                    summary.ingestedCount(),
                    summary.pendingCount(),
                    summary.failedCount(),
                    "Vector ingest already in progress for parentPageId="
                            + parentPageId
                            + ". Watch "
                            + manifestPath
                            + " and logs/app.log.");
        }

        jobPublisher.publish(IngestionJobType.VECTOR_INGEST, request);
        return IngestionResponse.accepted(
                IngestionMode.VECTOR_INGEST,
                parentPageId,
                manifestPath,
                summary.totalPages(),
                summary.ingestedCount(),
                summary.pendingCount(),
                summary.failedCount(),
                "Vector ingest started in the background for chunked pages.");
    }

    public void scheduleVectorIngestIfRequested(IngestionRequest request) {
        if (!request.shouldIngestVectors()) {
            return;
        }
        String parentPageId = request.parentPageId();
        if (jobCoordinator.tryAcquireVectorIngest(parentPageId)) {
            jobPublisher.publish(IngestionJobType.VECTOR_INGEST, request);
        } else {
            log.warn(
                    "Skipped vector ingest for parentPageId={} because another vector ingest is active",
                    parentPageId);
        }
    }

    public void scheduleChunkBatchIfRequested(IngestionRequest request) {
        if (!request.shouldChunkMarkdown()) {
            return;
        }
        String parentPageId = request.parentPageId();
        if (jobCoordinator.tryAcquireChunkBatch(parentPageId)) {
            jobPublisher.publish(IngestionJobType.CHUNK, request);
        } else {
            log.warn(
                    "Skipped chunk batch for parentPageId={} because another chunk batch is active",
                    parentPageId);
        }
    }

    public void schedulePageTransformIfRequested(IngestionRequest request) {
        if (!request.shouldExtractMarkdown()) {
            return;
        }
        String parentPageId = request.parentPageId();
        if (jobCoordinator.tryAcquirePageTransform(parentPageId)) {
            jobPublisher.publish(IngestionJobType.PAGE_TRANSFORM, request);
        } else {
            log.warn(
                    "Skipped page transform for parentPageId={} because another transform is active",
                    parentPageId);
        }
    }

    @Observed(name = "ingestion.status")
    public IngestionStatus getStatus(String parentPageId) {
        String manifestPath = manifestService.manifestDisplayPath(parentPageId);
        String crawlProgressPath =
                fileStorageService.displayPath(fileStorageService.crawlProgressPath(parentPageId));
        String batchProgressPath =
                fileStorageService.displayPath(fileStorageService.batchProgressPath(parentPageId));
        boolean manifestExists = manifestService.manifestExists(parentPageId);

        Map<String, Object> crawlProgress = fileStorageService
                .readJsonMapIfExists(fileStorageService.crawlProgressPath(parentPageId))
                .orElse(null);
        Map<String, Object> batchProgress = fileStorageService
                .readJsonMapIfExists(fileStorageService.batchProgressPath(parentPageId))
                .orElse(null);
        String activeJob = jobCoordinator.getActiveJob(parentPageId).map(Enum::name).orElse(null);

        if (!manifestExists) {
            return new IngestionStatus(
                    parentPageId,
                    manifestPath,
                    crawlProgressPath,
                    batchProgressPath,
                    false,
                    crawlProgress,
                    batchProgress,
                    activeJob,
                    0,
                    0,
                    0,
                    0,
                    buildStatusMessage(crawlProgress, batchProgress, activeJob, false));
        }

        try {
            PageManifest manifest = manifestService.loadManifest(parentPageId);
            ManifestSummary summary = manifestService.summarize(manifest);
            return new IngestionStatus(
                    parentPageId,
                    manifestPath,
                    crawlProgressPath,
                    batchProgressPath,
                    true,
                    crawlProgress,
                    batchProgress,
                    activeJob,
                    summary.totalPages(),
                    summary.ingestedCount(),
                    summary.pendingCount(),
                    summary.failedCount(),
                    buildStatusMessage(crawlProgress, batchProgress, activeJob, true));
        } catch (IOException ex) {
            log.warn("Could not read manifest for parentPageId={}: {}", parentPageId, ex.getMessage());
            return new IngestionStatus(
                    parentPageId,
                    manifestPath,
                    crawlProgressPath,
                    batchProgressPath,
                    true,
                    crawlProgress,
                    batchProgress,
                    activeJob,
                    0,
                    0,
                    0,
                    0,
                    "manifest.json exists but could not be parsed: " + ex.getMessage());
        }
    }

    private static String buildStatusMessage(
            Map<String, Object> crawlProgress,
            Map<String, Object> batchProgress,
            String activeJob,
            boolean manifestExists) {
        StringBuilder message = new StringBuilder();
        if (crawlProgress != null && crawlProgress.get("status") != null) {
            message.append("crawl=").append(crawlProgress.get("status"));
        }
        if (batchProgress != null && batchProgress.get("status") != null) {
            if (!message.isEmpty()) {
                message.append("; ");
            }
            Object phase = batchProgress.get("phase");
            message.append("batch");
            if (phase != null) {
                message.append("(").append(phase).append(")");
            }
            message.append("=").append(batchProgress.get("status"));
        }
        if (activeJob != null) {
            if (!message.isEmpty()) {
                message.append("; ");
            }
            message.append("activeJob=").append(activeJob);
        }
        if (!message.isEmpty()) {
            message.append("; ");
        }
        message.append(manifestExists ? "manifest present" : "manifest absent");
        return message.toString();
    }

    private ManifestSummary readSummary(String parentPageId) throws IOException {
        PageManifest manifest = manifestService.loadManifest(parentPageId);
        return manifestService.summarize(manifest);
    }

    private static ManifestSummary emptySummary() {
        return new ManifestSummary(0, 0, 0, 0);
    }

    private void logResolvedRequestOptions(IngestionRequest request) {
        log.info(
                "Ingestion options parentPageId={} batchSize={} concurrency={} timeoutSeconds={} verifySsl={} extractMarkdown={} chunkMarkdown={} ingestVectors={}",
                request.parentPageId(),
                request.resolvedBatchSize(properties.defaultBatchSize()),
                request.resolvedConcurrency(properties.defaultConcurrency()),
                request.resolvedRequestTimeoutSeconds(properties.defaultRequestTimeoutSeconds()),
                properties.verifySsl(),
                request.shouldExtractMarkdown(),
                request.shouldChunkMarkdown(),
                request.shouldIngestVectors());
    }

    private int countPendingChunks(String parentPageId) throws IOException {
        PageManifest manifest = manifestService.loadManifest(parentPageId);
        if (manifest.getPages() == null) {
            return 0;
        }
        int pending = 0;
        for (PageManifestEntry entry : manifest.getPages()) {
            if (manifestService.isPendingForChunking(entry)) {
                pending++;
            }
        }
        return pending;
    }

    private int countPendingVectorIngest(String parentPageId) throws IOException {
        PageManifest manifest = manifestService.loadManifest(parentPageId);
        if (manifest.getPages() == null) {
            return 0;
        }
        int pending = 0;
        for (PageManifestEntry entry : manifest.getPages()) {
            if (manifestService.isPendingForVectorIngest(entry)) {
                pending++;
            }
        }
        return pending;
    }
}
