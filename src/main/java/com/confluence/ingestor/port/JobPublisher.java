package com.confluence.ingestor.port;

import com.confluence.ingestor.api.dto.IngestionRequest;
import com.confluence.ingestor.messaging.IngestionJobType;

/**
 * Publishes ingestion stage jobs — in-process (Phase 11) or Kafka (Phase 12).
 */
public interface JobPublisher {

    void publish(IngestionJobType jobType, IngestionRequest request);
}
