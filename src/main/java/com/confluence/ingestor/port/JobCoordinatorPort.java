package com.confluence.ingestor.port;

import com.confluence.ingestor.service.IngestionActiveJob;

import java.util.Optional;

/**
 * Per-parent job exclusion — in-memory today; partition-aware when Kafka consumers own jobs.
 */
public interface JobCoordinatorPort {

    boolean tryAcquireManifestBuild(String parentPageId);

    void releaseManifestBuild(String parentPageId);

    boolean tryAcquirePageTransform(String parentPageId);

    void releasePageTransform(String parentPageId);

    boolean tryAcquireChunkBatch(String parentPageId);

    void releaseChunkBatch(String parentPageId);

    boolean tryAcquireVectorIngest(String parentPageId);

    void releaseVectorIngest(String parentPageId);

    Optional<IngestionActiveJob> getActiveJob(String parentPageId);
}
