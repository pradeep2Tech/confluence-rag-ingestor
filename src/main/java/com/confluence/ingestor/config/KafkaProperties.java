package com.confluence.ingestor.config;

import com.confluence.ingestor.messaging.IngestionJobType;
import com.confluence.ingestor.messaging.IngestionTopics;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "confluence.ingestor.kafka")
public record KafkaProperties(
        boolean enabled,
        String bootstrapServers,
        String consumerGroupId,
        String manifestCrawlTopic,
        String pageTransformTopic,
        String chunkTopic,
        String vectorIngestTopic) {

    public String resolveTopic(IngestionJobType jobType) {
        return switch (jobType) {
            case MANIFEST_CRAWL -> manifestCrawlTopic != null ? manifestCrawlTopic : IngestionTopics.MANIFEST_CRAWL;
            case PAGE_TRANSFORM -> pageTransformTopic != null ? pageTransformTopic : IngestionTopics.PAGE_TRANSFORM;
            case CHUNK -> chunkTopic != null ? chunkTopic : IngestionTopics.CHUNK;
            case VECTOR_INGEST -> vectorIngestTopic != null ? vectorIngestTopic : IngestionTopics.VECTOR_INGEST;
        };
    }
}
