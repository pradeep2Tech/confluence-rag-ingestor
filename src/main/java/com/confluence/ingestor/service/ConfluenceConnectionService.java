package com.confluence.ingestor.service;

import com.confluence.ingestor.api.dto.ConfluenceTestResponse;
import com.confluence.ingestor.config.IngestorProperties;
import com.confluence.ingestor.confluence.ConfluenceClient;
import com.confluence.ingestor.confluence.ConfluenceClientError;
import com.confluence.ingestor.confluence.ConfluenceClientFactory;
import com.confluence.ingestor.confluence.ConfluenceTargetResolver;
import com.confluence.ingestor.confluence.dto.ConfluencePageDto;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ConfluenceConnectionService {

    private final ConfluenceClientFactory clientFactory;
    private final ConfluenceTargetResolver targetResolver;
    private final IngestorProperties properties;
    private final RuntimeConfigService runtimeConfigService;

    public ConfluenceConnectionService(
            ConfluenceClientFactory clientFactory,
            ConfluenceTargetResolver targetResolver,
            IngestorProperties properties,
            RuntimeConfigService runtimeConfigService) {
        this.clientFactory = clientFactory;
        this.targetResolver = targetResolver;
        this.properties = properties;
        this.runtimeConfigService = runtimeConfigService;
    }

    public ConfluenceTestResponse testConnection(String baseUrl, String pat, String target, Boolean verifySsl) {
        String resolvedBaseUrl = baseUrl != null && !baseUrl.isBlank()
                ? baseUrl
                : runtimeConfigService.currentConfig().confluenceBaseUrl();
        String resolvedPat = pat != null && !pat.isBlank() ? pat : runtimeConfigService.currentPat();
        String resolvedTarget = target != null && !target.isBlank()
                ? target
                : runtimeConfigService.currentConfig().confluenceTarget();
        boolean resolvedVerifySsl = verifySsl != null ? verifySsl : runtimeConfigService.currentConfig().verifySsl();

        if (resolvedBaseUrl == null || resolvedBaseUrl.isBlank()) {
            return ConfluenceTestResponse.failure("Confluence base URL is required");
        }
        if (resolvedPat == null || resolvedPat.isBlank()) {
            return ConfluenceTestResponse.failure("Personal access token is required");
        }

        try {
            ConfluenceClient client = clientFactory.create(
                    resolvedBaseUrl, resolvedPat, resolvedVerifySsl, properties.defaultRequestTimeoutSeconds());
            Map<String, Object> user = client.getCurrentUser();
            String displayName = user != null && user.get("displayName") != null
                    ? user.get("displayName").toString()
                    : "authenticated user";

            ConfluenceTargetResolver.ResolvedTarget resolved = targetResolver.resolve(
                    resolvedBaseUrl,
                    resolvedPat,
                    resolvedVerifySsl,
                    properties.defaultRequestTimeoutSeconds(),
                    resolvedTarget);

            String pageTitle = null;
            if (resolved.success()) {
                ConfluencePageDto page = client.getContent(resolved.parentPageId(), "space,version");
                pageTitle = page.getTitle();
                return ConfluenceTestResponse.success(
                        displayName,
                        resolved.parentPageId(),
                        pageTitle,
                        resolved.spaceKey(),
                        "Connected to Confluence as " + displayName);
            }

            return ConfluenceTestResponse.success(
                    displayName, null, null, null, "Connected to Confluence as " + displayName);
        } catch (ConfluenceClientError ex) {
            return ConfluenceTestResponse.failure(ex.getMessage());
        } catch (Exception ex) {
            return ConfluenceTestResponse.failure("Connection failed: " + ex.getMessage());
        }
    }
}
