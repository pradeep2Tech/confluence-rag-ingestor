package com.confluence.ingestor.service;

import com.confluence.ingestor.api.dto.ComponentHealthDto;
import com.confluence.ingestor.api.dto.HealthResponseDto;
import com.confluence.ingestor.model.RuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Runs concurrent connectivity checks against the application, ChromaDB, and Ollama.
 */
@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration OVERALL_TIMEOUT = Duration.ofSeconds(5);

    private final RuntimeConfigService runtimeConfigService;
    private final RestClient restClient;

    public HealthService(RuntimeConfigService runtimeConfigService) {
        this.runtimeConfigService = runtimeConfigService;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public HealthResponseDto getHealth() {
        RuntimeConfig config = runtimeConfigService.currentConfig();
        ComponentHealthDto application = ComponentHealthDto.up("Application server is running");

        CompletableFuture<ComponentHealthDto> vectorStoreFuture =
                CompletableFuture.supplyAsync(() -> checkChroma(config));
        CompletableFuture<ComponentHealthDto> modelFuture =
                CompletableFuture.supplyAsync(() -> checkOllama(config));

        try {
            CompletableFuture.allOf(vectorStoreFuture, modelFuture)
                    .get(OVERALL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Health check interrupted");
        } catch (Exception ex) {
            log.debug("Health check timed out or failed waiting for dependencies: {}", ex.getMessage());
        }

        return new HealthResponseDto(
                application,
                resolveFuture(vectorStoreFuture, "Vector store check timed out"),
                resolveFuture(modelFuture, "Model check timed out"));
    }

    private static ComponentHealthDto resolveFuture(
            CompletableFuture<ComponentHealthDto> future, String timeoutMessage) {
        if (future.isDone() && !future.isCompletedExceptionally()) {
            return future.join();
        }
        return ComponentHealthDto.down(timeoutMessage);
    }

    private ComponentHealthDto checkChroma(RuntimeConfig config) {
        String baseUrl = normalizeBaseUrl(config.chromaHost()) + ":" + config.chromaPort();
        String heartbeatUrl = baseUrl + "/api/v2/heartbeat";
        try {
            restClient.get().uri(heartbeatUrl).retrieve().toBodilessEntity();
            return ComponentHealthDto.up("Chroma heartbeat OK at " + baseUrl);
        } catch (RestClientException ex) {
            log.debug("Chroma health check failed url={} error={}", heartbeatUrl, ex.getMessage());
            return ComponentHealthDto.down("Chroma unreachable at " + baseUrl);
        }
    }

    private ComponentHealthDto checkOllama(RuntimeConfig config) {
        String baseUrl = normalizeBaseUrl(config.ollamaBaseUrl());
        String tagsUrl = baseUrl + "/api/tags";
        try {
            restClient.get().uri(tagsUrl).retrieve().toBodilessEntity();
            String modelLabel = config.llmModel() != null && !config.llmModel().isBlank()
                    ? config.llmModel()
                    : "configured model";
            return ComponentHealthDto.up("Ollama reachable (" + modelLabel + ")");
        } catch (RestClientException ex) {
            log.debug("Ollama health check failed url={} error={}", tagsUrl, ex.getMessage());
            return ComponentHealthDto.down("Ollama unreachable at " + baseUrl);
        }
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
