package com.confluence.ingestor.service;

import com.confluence.ingestor.api.dto.IngestionRequest;
import com.confluence.ingestor.confluence.ConfluenceClient;
import com.confluence.ingestor.confluence.ConfluenceClientError;
import com.confluence.ingestor.confluence.ConfluenceClientFactory;
import com.confluence.ingestor.confluence.PageCrawler;
import com.confluence.ingestor.confluence.dto.ConfluencePageDto;
import com.confluence.ingestor.config.IngestorProperties;
import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.storage.CrawlProgressService;
import com.confluence.ingestor.storage.CrawlProgressService.CrawlProgressTracker;
import com.confluence.ingestor.storage.FileStorageService;
import com.confluence.ingestor.storage.ManifestService;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * Background Confluence tree crawl that populates {@code manifest.json}.
 */
@Service
public class ManifestCrawlService {

    private static final Logger log = LoggerFactory.getLogger(ManifestCrawlService.class);
    private static final String CONTENT_EXPAND = "space,version";

    private final IngestorProperties properties;
    private final ConfluenceClientFactory clientFactory;
    private final PageCrawler pageCrawler;
    private final ManifestService manifestService;
    private final FileStorageService fileStorageService;
    private final CrawlProgressService crawlProgressService;
    private final PageTransformBatchService pageTransformBatchService;
    private final IngestionJobCoordinator jobCoordinator;

    public ManifestCrawlService(
            IngestorProperties properties,
            ConfluenceClientFactory clientFactory,
            PageCrawler pageCrawler,
            ManifestService manifestService,
            FileStorageService fileStorageService,
            CrawlProgressService crawlProgressService,
            PageTransformBatchService pageTransformBatchService,
            IngestionJobCoordinator jobCoordinator) {
        this.properties = properties;
        this.clientFactory = clientFactory;
        this.pageCrawler = pageCrawler;
        this.manifestService = manifestService;
        this.fileStorageService = fileStorageService;
        this.crawlProgressService = crawlProgressService;
        this.pageTransformBatchService = pageTransformBatchService;
        this.jobCoordinator = jobCoordinator;
    }

    @Async("ingestionTaskExecutor")
    @Observed(name = "ingestion.manifest.crawl")
    public void runManifestCrawlAsync(IngestionRequest request) {
        String parentPageId = request.parentPageId();
        CrawlProgressTracker tracker = crawlProgressService.newTracker(parentPageId);
        boolean completed = false;
        try {
            fileStorageService.ensureParentDataLayout(parentPageId);

            int timeout = request.resolvedRequestTimeoutSeconds(properties.defaultRequestTimeoutSeconds());
            ConfluenceClient client = clientFactory.create(request.baseUrl(), request.pat(), timeout);

            log.info("Background manifest crawl started for parentPageId={}", parentPageId);

            ConfluencePageDto parentDoc = client.getContent(parentPageId, CONTENT_EXPAND);
            String parentTitle = parentDoc.getTitle() != null ? parentDoc.getTitle() : "";
            tracker.bootstrapRunning(parentPageId, parentTitle);

            List<ConfluencePageDto> descendants = pageCrawler.fetchAllDescendantPages(
                    client,
                    parentPageId,
                    parentTitle,
                    (processed, totalDiscovered, queueSize, currentPageId, currentTitle) ->
                            tracker.onCrawlTick(
                                    processed, totalDiscovered, queueSize, currentPageId, currentTitle));

            PageManifest manifest = manifestService.buildFullManifest(
                    request.baseUrl(), parentPageId, parentDoc, descendants, client);
            manifestService.saveManifest(parentPageId, manifest);

            int pageCount = manifest.getPages() != null ? manifest.getPages().size() : 0;
            tracker.writeCompleted(pageCount);
            log.info("Background manifest crawl finished for parentPageId={} ({} pages)", parentPageId, pageCount);
            completed = true;
        } catch (ConfluenceClientError ex) {
            tracker.writeFailed(ex.getMessage());
            log.warn("Background manifest crawl failed for parentPageId={}: {}", parentPageId, ex.getMessage());
        } catch (IOException ex) {
            tracker.writeFailed(ex.getMessage());
            log.warn("Background manifest crawl I/O error for parentPageId={}: {}", parentPageId, ex.getMessage());
        } catch (Exception ex) {
            tracker.writeFailed(ex.getMessage());
            log.error("Background manifest crawl error for parentPageId={}", parentPageId, ex);
        } finally {
            jobCoordinator.releaseManifestBuild(parentPageId);
        }

        if (completed && request.shouldExtractMarkdown()) {
            scheduleChainedPageTransform(request, parentPageId);
        }
    }

    private void scheduleChainedPageTransform(IngestionRequest request, String parentPageId) {
        if (jobCoordinator.tryAcquirePageTransform(parentPageId)) {
            pageTransformBatchService.runPageTransformBatchAsync(request);
            log.info("Chained page transform scheduled for parentPageId={}", parentPageId);
        } else {
            log.warn(
                    "Skipped chained page transform for parentPageId={} because another transform is active",
                    parentPageId);
        }
    }
}
