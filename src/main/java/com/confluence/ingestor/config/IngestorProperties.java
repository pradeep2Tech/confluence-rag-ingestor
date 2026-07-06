package com.confluence.ingestor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "confluence.ingestor")
public record IngestorProperties(
        String dataDirectory,
        int defaultBatchSize,
        int defaultConcurrency,
        int defaultRequestTimeoutSeconds,
        boolean verifySsl,
        int manifestVersion,
        int retrySkipThreshold,
        List<String> allowedImageExtensions,
        List<String> allowedDrawioExtensions,
        int maxChunkCharacters,
        boolean vectorIngestEnabled,
        String chromaCollectionName,
        boolean queryEnabled,
        int defaultQueryTopK,
        double defaultSimilarityThreshold,
        boolean perPageStateEnabled,
        int asyncCorePoolSize,
        int asyncMaxPoolSize) {
}
