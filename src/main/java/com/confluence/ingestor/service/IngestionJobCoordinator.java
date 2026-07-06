package com.confluence.ingestor.service;

import com.confluence.ingestor.port.JobCoordinatorPort;
import com.confluence.ingestor.service.IngestionActiveJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-parent job exclusion for manifest crawl (Phase 10 extends for batch jobs).
 */
@Component
public class IngestionJobCoordinator implements JobCoordinatorPort {

    private static final Logger log = LoggerFactory.getLogger(IngestionJobCoordinator.class);

    private final Set<String> manifestBuildActive = ConcurrentHashMap.newKeySet();
    private final Set<String> pageTransformActive = ConcurrentHashMap.newKeySet();
    private final Set<String> chunkBatchActive = ConcurrentHashMap.newKeySet();
    private final Set<String> vectorIngestActive = ConcurrentHashMap.newKeySet();

    @Override
    public boolean tryAcquireManifestBuild(String parentPageId) {
        if (pageTransformActive.contains(parentPageId)
                || chunkBatchActive.contains(parentPageId)
                || vectorIngestActive.contains(parentPageId)) {
            log.info("Manifest crawl rejected — another job active parentPageId={}", parentPageId);
            return false;
        }
        boolean acquired = manifestBuildActive.add(parentPageId);
        if (acquired) {
            log.debug("Acquired manifest crawl lock parentPageId={}", parentPageId);
        } else {
            log.info("Manifest crawl already running parentPageId={}", parentPageId);
        }
        return acquired;
    }

    @Override
    public void releaseManifestBuild(String parentPageId) {
        manifestBuildActive.remove(parentPageId);
        log.debug("Released manifest crawl lock parentPageId={}", parentPageId);
    }

    public boolean isManifestBuildActive(String parentPageId) {
        return manifestBuildActive.contains(parentPageId);
    }

    @Override
    public boolean tryAcquirePageTransform(String parentPageId) {
        if (manifestBuildActive.contains(parentPageId)
                || chunkBatchActive.contains(parentPageId)
                || vectorIngestActive.contains(parentPageId)) {
            log.info("Page transform rejected — another job active parentPageId={}", parentPageId);
            return false;
        }
        boolean acquired = pageTransformActive.add(parentPageId);
        if (acquired) {
            log.debug("Acquired page transform lock parentPageId={}", parentPageId);
        } else {
            log.info("Page transform already running parentPageId={}", parentPageId);
        }
        return acquired;
    }

    @Override
    public void releasePageTransform(String parentPageId) {
        pageTransformActive.remove(parentPageId);
        log.debug("Released page transform lock parentPageId={}", parentPageId);
    }

    public boolean isPageTransformActive(String parentPageId) {
        return pageTransformActive.contains(parentPageId);
    }

    @Override
    public boolean tryAcquireChunkBatch(String parentPageId) {
        if (manifestBuildActive.contains(parentPageId)
                || pageTransformActive.contains(parentPageId)
                || vectorIngestActive.contains(parentPageId)) {
            log.info("Chunk batch rejected — another job active parentPageId={}", parentPageId);
            return false;
        }
        boolean acquired = chunkBatchActive.add(parentPageId);
        if (acquired) {
            log.debug("Acquired chunk batch lock parentPageId={}", parentPageId);
        } else {
            log.info("Chunk batch already running parentPageId={}", parentPageId);
        }
        return acquired;
    }

    @Override
    public void releaseChunkBatch(String parentPageId) {
        chunkBatchActive.remove(parentPageId);
        log.debug("Released chunk batch lock parentPageId={}", parentPageId);
    }

    public boolean isChunkBatchActive(String parentPageId) {
        return chunkBatchActive.contains(parentPageId);
    }

    @Override
    public boolean tryAcquireVectorIngest(String parentPageId) {
        if (manifestBuildActive.contains(parentPageId)
                || pageTransformActive.contains(parentPageId)
                || chunkBatchActive.contains(parentPageId)) {
            log.info("Vector ingest rejected — another job active parentPageId={}", parentPageId);
            return false;
        }
        boolean acquired = vectorIngestActive.add(parentPageId);
        if (acquired) {
            log.debug("Acquired vector ingest lock parentPageId={}", parentPageId);
        } else {
            log.info("Vector ingest already running parentPageId={}", parentPageId);
        }
        return acquired;
    }

    @Override
    public void releaseVectorIngest(String parentPageId) {
        vectorIngestActive.remove(parentPageId);
        log.debug("Released vector ingest lock parentPageId={}", parentPageId);
    }

    public boolean isVectorIngestActive(String parentPageId) {
        return vectorIngestActive.contains(parentPageId);
    }

    @Override
    public Optional<IngestionActiveJob> getActiveJob(String parentPageId) {
        if (manifestBuildActive.contains(parentPageId)) {
            return Optional.of(IngestionActiveJob.MANIFEST_CRAWL);
        }
        if (pageTransformActive.contains(parentPageId)) {
            return Optional.of(IngestionActiveJob.PAGE_TRANSFORM);
        }
        if (chunkBatchActive.contains(parentPageId)) {
            return Optional.of(IngestionActiveJob.CHUNK);
        }
        if (vectorIngestActive.contains(parentPageId)) {
            return Optional.of(IngestionActiveJob.VECTOR_INGEST);
        }
        return Optional.empty();
    }
}
