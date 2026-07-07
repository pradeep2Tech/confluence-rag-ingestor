package com.confluence.ingestor.service;

import com.confluence.ingestor.api.dto.ConfigRequest;
import com.confluence.ingestor.api.dto.ConfigResponse;
import com.confluence.ingestor.config.IngestorProperties;
import com.confluence.ingestor.confluence.ConfluenceTargetResolver;
import com.confluence.ingestor.model.RuntimeConfig;
import com.confluence.ingestor.util.SecretMaskingUtil;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds runtime UI configuration and PAT in memory. Optionally persists non-secret fields and encrypted PAT to disk.
 */
@Service
public class RuntimeConfigService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeConfigService.class);
    private static final String ENV_PAT = "CONFLUENCE_PAT";
    private static final String ENV_BASE_URL = "CONFLUENCE_BASE_URL";
    private static final String ENV_TARGET = "CONFLUENCE_TARGET";

    private final IngestorProperties ingestorProperties;
    private final EncryptionService encryptionService;
    private final ConfluenceTargetResolver targetResolver;
    private final ObjectMapper objectMapper;
    private final Path configFile;

    private final AtomicReference<RuntimeConfig> config = new AtomicReference<>();
    private volatile String pat;

    public RuntimeConfigService(
            IngestorProperties ingestorProperties,
            EncryptionService encryptionService,
            ConfluenceTargetResolver targetResolver,
            ObjectMapper objectMapper,
            @Value("${confluence.ingestor.ui-config-file:data/ui-config.json}") String configFilePath) {
        this.ingestorProperties = ingestorProperties;
        this.encryptionService = encryptionService;
        this.targetResolver = targetResolver;
        this.objectMapper = objectMapper;
        this.configFile = Path.of(configFilePath);
        initialize();
    }

    public ConfigResponse getMaskedConfig() {
        RuntimeConfig current = config.get();
        return toResponse(current, SecretMaskingUtil.maskSecret(pat));
    }

    public ConfigResponse save(ConfigRequest request) {
        RuntimeConfig current = config.get();
        String newPat = resolvePat(request.pat(), pat);
        if (request.pat() != null && !request.pat().isBlank() && !SecretMaskingUtil.isMaskedValue(request.pat())) {
            newPat = request.pat().strip();
        }

        String baseUrl = coalesce(request.confluenceBaseUrl(), current.confluenceBaseUrl());
        String target = coalesce(request.confluenceTarget(), current.confluenceTarget());
        String parentPageId = current.parentPageId();
        String spaceKey = current.spaceKey();

        if (target != null && !target.isBlank() && newPat != null && !newPat.isBlank() && baseUrl != null && !baseUrl.isBlank()) {
            ConfluenceTargetResolver.ResolvedTarget resolved = targetResolver.resolve(
                    baseUrl,
                    newPat,
                    request.verifySsl() != null ? request.verifySsl() : current.verifySsl(),
                    ingestorProperties.defaultRequestTimeoutSeconds(),
                    target);
            if (resolved.success()) {
                parentPageId = resolved.parentPageId();
                spaceKey = resolved.spaceKey();
            }
        } else if (target != null && !target.isBlank()) {
            String extracted = targetResolver.extractPageId(target);
            if (extracted != null) {
                parentPageId = extracted;
            }
        }

        RuntimeConfig updated = new RuntimeConfig(
                coalesce(request.llmProvider(), current.llmProvider()),
                coalesce(request.llmModel(), current.llmModel()),
                coalesce(request.ollamaBaseUrl(), current.ollamaBaseUrl()),
                coalesce(request.embeddingModel(), current.embeddingModel()),
                baseUrl != null ? baseUrl.strip().replaceAll("/+$", "") : "",
                target != null ? target.strip() : "",
                parentPageId != null ? parentPageId : "",
                spaceKey,
                coalesce(request.vectorStore(), current.vectorStore()),
                coalesce(request.chromaHost(), current.chromaHost()),
                request.chromaPort() != null ? request.chromaPort() : current.chromaPort(),
                coalesce(request.chromaCollectionName(), current.chromaCollectionName()),
                request.verifySsl() != null ? request.verifySsl() : current.verifySsl(),
                newPat != null && !newPat.isBlank());

        pat = newPat;
        config.set(updated);
        persist(updated);
        log.info("Runtime configuration saved parentPageId={} patConfigured={}", updated.parentPageId(), updated.patConfigured());
        return toResponse(updated, SecretMaskingUtil.maskSecret(pat));
    }

    public RuntimeConfig currentConfig() {
        return config.get();
    }

    public String currentPat() {
        return pat;
    }

    public boolean isReadyForIngestion() {
        RuntimeConfig current = config.get();
        return current.confluenceBaseUrl() != null
                && !current.confluenceBaseUrl().isBlank()
                && current.parentPageId() != null
                && !current.parentPageId().isBlank()
                && pat != null
                && !pat.isBlank();
    }

    private void initialize() {
        RuntimeConfig defaults = buildDefaultsFromEnvironment();
        config.set(defaults);
        pat = System.getenv(ENV_PAT);

        if (Files.isRegularFile(configFile)) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> stored = objectMapper.readValue(Files.readString(configFile), Map.class);
                RuntimeConfig loaded = mergeFromMap(defaults, stored);
                config.set(loaded);
                Object encryptedPat = stored.get("encryptedPat");
                if (encryptedPat instanceof String encrypted && encryptionService.isEnabled()) {
                    pat = encryptionService.decrypt(encrypted);
                }
                log.info("Loaded UI configuration from {}", configFile);
            } catch (IOException ex) {
                log.warn("Could not load UI configuration from {}: {}", configFile, ex.getMessage());
            }
        }

        if (pat != null && !pat.isBlank()) {
            config.set(config.get().withPatConfigured(true));
        }
    }

    private RuntimeConfig buildDefaultsFromEnvironment() {
        RuntimeConfig defaults = RuntimeConfig.defaults();
        String baseUrl = System.getenv(ENV_BASE_URL);
        String target = System.getenv(ENV_TARGET);
        String parentPageId = "";
        if (target != null && !target.isBlank()) {
            parentPageId = targetResolver.extractPageId(target);
            if (parentPageId == null) {
                parentPageId = "";
            }
        }
        return new RuntimeConfig(
                defaults.llmProvider(),
                defaults.llmModel(),
                defaults.ollamaBaseUrl(),
                defaults.embeddingModel(),
                baseUrl != null ? baseUrl : "",
                target != null ? target : "",
                parentPageId,
                null,
                defaults.vectorStore(),
                defaults.chromaHost(),
                defaults.chromaPort(),
                ingestorProperties.chromaCollectionName(),
                ingestorProperties.verifySsl(),
                pat != null && !pat.isBlank());
    }

    @SuppressWarnings("unchecked")
    private RuntimeConfig mergeFromMap(RuntimeConfig defaults, Map<String, Object> stored) {
        return new RuntimeConfig(
                stringValue(stored.get("llmProvider"), defaults.llmProvider()),
                stringValue(stored.get("llmModel"), defaults.llmModel()),
                stringValue(stored.get("ollamaBaseUrl"), defaults.ollamaBaseUrl()),
                stringValue(stored.get("embeddingModel"), defaults.embeddingModel()),
                stringValue(stored.get("confluenceBaseUrl"), defaults.confluenceBaseUrl()),
                stringValue(stored.get("confluenceTarget"), defaults.confluenceTarget()),
                stringValue(stored.get("parentPageId"), defaults.parentPageId()),
                (String) stored.get("spaceKey"),
                stringValue(stored.get("vectorStore"), defaults.vectorStore()),
                stringValue(stored.get("chromaHost"), defaults.chromaHost()),
                intValue(stored.get("chromaPort"), defaults.chromaPort()),
                stringValue(stored.get("chromaCollectionName"), defaults.chromaCollectionName()),
                boolValue(stored.get("verifySsl"), defaults.verifySsl()),
                defaults.patConfigured());
    }

    private void persist(RuntimeConfig updated) {
        try {
            Files.createDirectories(configFile.getParent());
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("llmProvider", updated.llmProvider());
            payload.put("llmModel", updated.llmModel());
            payload.put("ollamaBaseUrl", updated.ollamaBaseUrl());
            payload.put("embeddingModel", updated.embeddingModel());
            payload.put("confluenceBaseUrl", updated.confluenceBaseUrl());
            payload.put("confluenceTarget", updated.confluenceTarget());
            payload.put("parentPageId", updated.parentPageId());
            payload.put("spaceKey", updated.spaceKey());
            payload.put("vectorStore", updated.vectorStore());
            payload.put("chromaHost", updated.chromaHost());
            payload.put("chromaPort", updated.chromaPort());
            payload.put("chromaCollectionName", updated.chromaCollectionName());
            payload.put("verifySsl", updated.verifySsl());
            if (encryptionService.isEnabled() && pat != null && !pat.isBlank()) {
                payload.put("encryptedPat", encryptionService.encrypt(pat));
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), payload);
        } catch (IOException ex) {
            log.warn("Could not persist UI configuration to {}: {}", configFile, ex.getMessage());
        }
    }

    private static ConfigResponse toResponse(RuntimeConfig config, String maskedPat) {
        return new ConfigResponse(
                config.llmProvider(),
                config.llmModel(),
                config.ollamaBaseUrl(),
                config.embeddingModel(),
                config.confluenceBaseUrl(),
                config.confluenceTarget(),
                config.parentPageId(),
                config.spaceKey(),
                config.vectorStore(),
                config.chromaHost(),
                config.chromaPort(),
                config.chromaCollectionName(),
                config.verifySsl(),
                maskedPat,
                config.patConfigured());
    }

    private static String coalesce(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private static String resolvePat(String requestPat, String currentPat) {
        if (requestPat == null || requestPat.isBlank() || SecretMaskingUtil.isMaskedValue(requestPat)) {
            return currentPat;
        }
        return requestPat.strip();
    }

    private static String stringValue(Object value, String fallback) {
        return value != null ? value.toString() : fallback;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean boolValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return fallback;
    }
}
