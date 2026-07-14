package com.confluence.ingestor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.attachment-analysis")
public record AttachmentAnalysisProperties(
        boolean enabled,
        boolean visionEnabled,
        String visionModel,
        String ollamaBaseUrl,
        Duration timeout,
        int maxFileSizeMb,
        int schemaVersion) {

    public AttachmentAnalysisProperties {
        if (visionModel == null || visionModel.isBlank()) {
            visionModel = "qwen3-vl:8b";
        }
        if (ollamaBaseUrl == null || ollamaBaseUrl.isBlank()) {
            ollamaBaseUrl = "http://localhost:11434";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(120);
        }
        if (maxFileSizeMb <= 0) {
            maxFileSizeMb = 20;
        }
        if (schemaVersion <= 0) {
            schemaVersion = 1;
        }
    }

    public long maxFileSizeBytes() {
        return (long) maxFileSizeMb * 1024 * 1024;
    }
}
