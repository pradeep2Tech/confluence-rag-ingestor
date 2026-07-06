package com.confluence.ingestor.messaging;

import com.confluence.ingestor.api.dto.IngestionRequest;
import com.confluence.ingestor.port.JobPublisher;
import com.confluence.ingestor.service.ChunkBatchService;
import com.confluence.ingestor.service.ManifestCrawlService;
import com.confluence.ingestor.service.PageTransformBatchService;
import com.confluence.ingestor.service.VectorIngestBatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default in-process job publisher — invokes {@code @Async} batch services directly.
 */
@Component
@ConditionalOnProperty(name = "confluence.ingestor.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class InProcessJobPublisher implements JobPublisher {

    private static final Logger log = LoggerFactory.getLogger(InProcessJobPublisher.class);

    private final ManifestCrawlService manifestCrawlService;
    private final PageTransformBatchService pageTransformBatchService;
    private final ChunkBatchService chunkBatchService;
    private final VectorIngestBatchService vectorIngestBatchService;

    public InProcessJobPublisher(
            ManifestCrawlService manifestCrawlService,
            PageTransformBatchService pageTransformBatchService,
            ChunkBatchService chunkBatchService,
            VectorIngestBatchService vectorIngestBatchService) {
        this.manifestCrawlService = manifestCrawlService;
        this.pageTransformBatchService = pageTransformBatchService;
        this.chunkBatchService = chunkBatchService;
        this.vectorIngestBatchService = vectorIngestBatchService;
    }

    @Override
    public void publish(IngestionJobType jobType, IngestionRequest request) {
        log.info("In-process job published type={} parentPageId={}", jobType, request.parentPageId());
        switch (jobType) {
            case MANIFEST_CRAWL -> manifestCrawlService.runManifestCrawlAsync(request);
            case PAGE_TRANSFORM -> pageTransformBatchService.runPageTransformBatchAsync(request);
            case CHUNK -> chunkBatchService.runChunkBatchAsync(request);
            case VECTOR_INGEST -> vectorIngestBatchService.runVectorIngestBatchAsync(request);
        }
    }
}
