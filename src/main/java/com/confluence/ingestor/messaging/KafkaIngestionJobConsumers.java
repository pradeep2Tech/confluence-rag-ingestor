package com.confluence.ingestor.messaging;

import com.confluence.ingestor.port.JobCoordinatorPort;
import com.confluence.ingestor.service.ChunkBatchService;
import com.confluence.ingestor.service.ManifestCrawlService;
import com.confluence.ingestor.service.PageTransformBatchService;
import com.confluence.ingestor.service.VectorIngestBatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotent Kafka consumers for ingestion pipeline stages (Phase 12).
 */
@Component
@ConditionalOnProperty(name = "confluence.ingestor.kafka.enabled", havingValue = "true")
public class KafkaIngestionJobConsumers {

    private static final Logger log = LoggerFactory.getLogger(KafkaIngestionJobConsumers.class);

    private final Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();
    private final JobCoordinatorPort jobCoordinator;
    private final ManifestCrawlService manifestCrawlService;
    private final PageTransformBatchService pageTransformBatchService;
    private final ChunkBatchService chunkBatchService;
    private final VectorIngestBatchService vectorIngestBatchService;

    public KafkaIngestionJobConsumers(
            JobCoordinatorPort jobCoordinator,
            ManifestCrawlService manifestCrawlService,
            PageTransformBatchService pageTransformBatchService,
            ChunkBatchService chunkBatchService,
            VectorIngestBatchService vectorIngestBatchService) {
        this.jobCoordinator = jobCoordinator;
        this.manifestCrawlService = manifestCrawlService;
        this.pageTransformBatchService = pageTransformBatchService;
        this.chunkBatchService = chunkBatchService;
        this.vectorIngestBatchService = vectorIngestBatchService;
    }

    @KafkaListener(
            topics = "${confluence.ingestor.kafka.manifest-crawl-topic:ingestion.manifest.crawl}",
            containerFactory = "ingestionJobListenerContainerFactory")
    public void onManifestCrawl(IngestionJobMessage message, Acknowledgment ack) {
        if (!markProcessed(message)) {
            ack.acknowledge();
            return;
        }
        String parentPageId = message.parentPageId();
        if (jobCoordinator.tryAcquireManifestBuild(parentPageId)) {
            manifestCrawlService.runManifestCrawlAsync(message.request());
        } else {
            log.warn("Skipped duplicate manifest crawl for parentPageId={}", parentPageId);
        }
        ack.acknowledge();
    }

    @KafkaListener(
            topics = "${confluence.ingestor.kafka.page-transform-topic:ingestion.page.transform}",
            containerFactory = "ingestionJobListenerContainerFactory")
    public void onPageTransform(IngestionJobMessage message, Acknowledgment ack) {
        if (!markProcessed(message)) {
            ack.acknowledge();
            return;
        }
        String parentPageId = message.parentPageId();
        if (jobCoordinator.tryAcquirePageTransform(parentPageId)) {
            pageTransformBatchService.runPageTransformBatchAsync(message.request());
        } else {
            log.warn("Skipped duplicate page transform for parentPageId={}", parentPageId);
        }
        ack.acknowledge();
    }

    @KafkaListener(
            topics = "${confluence.ingestor.kafka.chunk-topic:ingestion.page.chunk}",
            containerFactory = "ingestionJobListenerContainerFactory")
    public void onChunk(IngestionJobMessage message, Acknowledgment ack) {
        if (!markProcessed(message)) {
            ack.acknowledge();
            return;
        }
        String parentPageId = message.parentPageId();
        if (jobCoordinator.tryAcquireChunkBatch(parentPageId)) {
            chunkBatchService.runChunkBatchAsync(message.request());
        } else {
            log.warn("Skipped duplicate chunk batch for parentPageId={}", parentPageId);
        }
        ack.acknowledge();
    }

    @KafkaListener(
            topics = "${confluence.ingestor.kafka.vector-ingest-topic:ingestion.page.vector}",
            containerFactory = "ingestionJobListenerContainerFactory")
    public void onVectorIngest(IngestionJobMessage message, Acknowledgment ack) {
        if (!markProcessed(message)) {
            ack.acknowledge();
            return;
        }
        String parentPageId = message.parentPageId();
        if (jobCoordinator.tryAcquireVectorIngest(parentPageId)) {
            vectorIngestBatchService.runVectorIngestBatchAsync(message.request());
        } else {
            log.warn("Skipped duplicate vector ingest for parentPageId={}", parentPageId);
        }
        ack.acknowledge();
    }

    private boolean markProcessed(IngestionJobMessage message) {
        if (message.messageId() == null) {
            return true;
        }
        return processedMessageIds.add(message.messageId());
    }
}
