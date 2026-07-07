package com.confluence.ingestor.confluence;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a Confluence page ID, page URL, or space key to a parent page ID for ingestion.
 */
@Component
public class ConfluenceTargetResolver {

    private static final Pattern PAGE_ID_PARAM = Pattern.compile("[?&]pageId=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGES_PATH = Pattern.compile("/pages/(\\d+)(?:/|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_ID = Pattern.compile("^\\d+$");
    private static final Pattern SPACE_KEY = Pattern.compile("^[A-Za-z0-9_-]+$");

    private final ConfluenceClientFactory clientFactory;

    public ConfluenceTargetResolver(ConfluenceClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    public ResolvedTarget resolve(String baseUrl, String pat, boolean verifySsl, int timeoutSeconds, String target) {
        if (target == null || target.isBlank()) {
            return ResolvedTarget.error("Confluence page URL, page ID, or space key is required");
        }
        String trimmed = target.strip();

        String pageId = extractPageId(trimmed);
        if (pageId != null) {
            return ResolvedTarget.page(pageId, trimmed, null);
        }

        if (SPACE_KEY.matcher(trimmed).matches() && !NUMERIC_ID.matcher(trimmed).matches()) {
            return resolveSpaceHomepage(baseUrl, pat, verifySsl, timeoutSeconds, trimmed);
        }

        return ResolvedTarget.error(
                "Could not resolve target. Use a page ID, Confluence page URL, or space key (e.g. ENG).");
    }

    public String extractPageId(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        String trimmed = target.strip();
        if (NUMERIC_ID.matcher(trimmed).matches()) {
            return trimmed;
        }
        Matcher paramMatcher = PAGE_ID_PARAM.matcher(trimmed);
        if (paramMatcher.find()) {
            return paramMatcher.group(1);
        }
        Matcher pathMatcher = PAGES_PATH.matcher(trimmed);
        if (pathMatcher.find()) {
            return pathMatcher.group(1);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private ResolvedTarget resolveSpaceHomepage(
            String baseUrl, String pat, boolean verifySsl, int timeoutSeconds, String spaceKey) {
        try {
            RestClient client = clientFactory.create(baseUrl, pat, verifySsl, timeoutSeconds).restClient();
            Map<String, Object> space = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/rest/api/space/{spaceKey}")
                            .queryParam("expand", "homepage")
                            .build(spaceKey))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            if (space == null) {
                return ResolvedTarget.error("Space not found: " + spaceKey);
            }
            Object homepage = space.get("homepage");
            if (!(homepage instanceof Map<?, ?> homepageMap)) {
                return ResolvedTarget.error("Space has no homepage: " + spaceKey);
            }
            Object id = homepageMap.get("id");
            if (id == null) {
                return ResolvedTarget.error("Space homepage has no ID: " + spaceKey);
            }
            return ResolvedTarget.page(id.toString(), spaceKey, spaceKey);
        } catch (RestClientResponseException ex) {
            return ResolvedTarget.error("Failed to resolve space " + spaceKey + ": HTTP " + ex.getStatusCode().value());
        } catch (Exception ex) {
            return ResolvedTarget.error("Failed to resolve space " + spaceKey + ": " + ex.getMessage());
        }
    }

    public record ResolvedTarget(boolean success, String parentPageId, String displayTarget, String spaceKey, String error) {
        public static ResolvedTarget page(String parentPageId, String displayTarget, String spaceKey) {
            return new ResolvedTarget(true, parentPageId, displayTarget, spaceKey, null);
        }

        public static ResolvedTarget error(String error) {
            return new ResolvedTarget(false, null, null, null, error);
        }
    }
}
