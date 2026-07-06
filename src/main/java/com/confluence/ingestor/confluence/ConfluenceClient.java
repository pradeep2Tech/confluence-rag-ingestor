package com.confluence.ingestor.confluence;

import com.confluence.ingestor.confluence.dto.ConfluenceChildListResponse;
import com.confluence.ingestor.confluence.dto.ConfluencePageContentDto;
import com.confluence.ingestor.confluence.dto.ConfluencePageDto;
import com.confluence.ingestor.port.ConfluencePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Confluence REST client — Bearer PAT only. Never log the token.
 * <p>
 * Mirrors Python {@code confluence_client.py}: child pagination via {@code _links.next}
 * or cumulative {@code start} offset (avoids skipping children on large trees).
 */
public class ConfluenceClient implements ConfluencePort {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceClient.class);

    static final int CHILD_PAGE_LIMIT = 100;
    public static final String PAGE_TRANSFORM_EXPAND = "body.storage,body.view,version,space,ancestors";

    private final String baseUrl;
    private final RestClient restClient;

    public ConfluenceClient(String baseUrl, RestClient restClient) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restClient = restClient;
    }

    public String baseUrl() {
        return baseUrl;
    }

    RestClient restClient() {
        return restClient;
    }

    public ConfluencePageDto getContent(String pageId, String expand) {
        return getContent(pageId, expand, ConfluencePageDto.class);
    }

    public ConfluencePageContentDto getPageContent(String pageId) {
        return getContent(pageId, PAGE_TRANSFORM_EXPAND, ConfluencePageContentDto.class);
    }

    public <T> T getContent(String pageId, String expand, Class<T> type) {
        log.debug("Confluence GET content pageId={} expand={}", pageId, expand);
        try {
            T body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/rest/api/content/{pageId}")
                            .queryParam("expand", expand)
                            .build(pageId))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(type);
            log.debug("Confluence GET content succeeded pageId={}", pageId);
            return body;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                log.warn("Confluence page not found pageId={}", pageId);
                throw new ConfluenceClientError("Page not found: " + pageId, 404);
            }
            log.warn("Confluence GET content failed pageId={} status={}", pageId, ex.getStatusCode().value());
            throw toClientError("GET content failed for pageId=" + pageId, ex);
        } catch (Exception ex) {
            log.warn("Confluence GET content error pageId={}: {}", pageId, ex.getMessage());
            throw new ConfluenceClientError("GET content failed for pageId=" + pageId + ": " + ex.getMessage());
        }
    }

    public String buildWebUrl(ConfluencePageContentDto page) {
        String pageId = page.getId() != null ? page.getId() : "";
        String webui = page.getLinks() != null ? page.getLinks().get("webui") : null;
        if (webui == null || webui.isBlank()) {
            return baseUrl + "/pages/viewpage.action?pageId=" + pageId;
        }
        if (webui.startsWith("http")) {
            return webui;
        }
        return absUrl(webui);
    }

    public String buildWebUrl(ConfluencePageDto page) {
        String pageId = page.getId() != null ? page.getId() : "";
        String webui = page.getLinks() != null ? page.getLinks().get("webui") : null;
        if (webui == null || webui.isBlank()) {
            return baseUrl + "/pages/viewpage.action?pageId=" + pageId;
        }
        if (webui.startsWith("http")) {
            return webui;
        }
        return absUrl(webui);
    }

    /**
     * All direct child pages of {@code pageId}, handling Confluence pagination.
     */
    public List<ConfluencePageDto> listDirectChildren(String pageId) {
        List<ConfluencePageDto> all = new ArrayList<>();
        for (ConfluencePageDto child : iterateDirectChildren(pageId)) {
            all.add(child);
        }
        return all;
    }

    public Iterable<ConfluencePageDto> iterateDirectChildren(String pageId) {
        return () -> new DirectChildIterator(pageId);
    }

    private ConfluenceChildListResponse fetchChildBatch(String pageId, int start, String nextUrl) {
        log.debug("Confluence list children pageId={} start={} hasNextUrl={}", pageId, start, nextUrl != null);
        try {
            if (nextUrl != null && !nextUrl.isBlank()) {
                String absolute = absUrl(nextUrl);
                return restClient.get()
                        .uri(URI.create(absolute))
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(ConfluenceChildListResponse.class);
            }
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/rest/api/content/{pageId}/child/page")
                            .queryParam("limit", CHILD_PAGE_LIMIT)
                            .queryParam("start", start)
                            .queryParam("expand", "version")
                            .build(pageId))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ConfluenceChildListResponse.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                log.debug("Confluence child list 404 pageId={} — treating as empty", pageId);
                return emptyChildList();
            }
            log.warn("Confluence child list failed pageId={} status={}", pageId, ex.getStatusCode().value());
            throw toClientError("Child page listing failed for pageId=" + pageId, ex);
        }
    }

    private static ConfluenceChildListResponse emptyChildList() {
        ConfluenceChildListResponse empty = new ConfluenceChildListResponse();
        empty.setResults(List.of());
        return empty;
    }

    private String absUrl(String pathOrUrl) {
        if (pathOrUrl.startsWith("http")) {
            return pathOrUrl;
        }
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        if (pathOrUrl.startsWith("/")) {
            return base + pathOrUrl.substring(1);
        }
        return base + pathOrUrl;
    }

    private static ConfluenceClientError toClientError(String prefix, RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String body = ex.getResponseBodyAsString();
        String snippet = body != null && body.length() > 500 ? body.substring(0, 500) : body;
        return new ConfluenceClientError(
                prefix + ": HTTP " + status.value() + " " + (snippet != null ? snippet : ""),
                status.value());
    }

    private final class DirectChildIterator implements Iterator<ConfluencePageDto> {

        private final String pageId;
        private int cumulativeStart;
        private String nextUrl;
        private List<ConfluencePageDto> batch = List.of();
        private int batchIndex;
        private boolean endOfStream;

        private DirectChildIterator(String pageId) {
            this.pageId = pageId;
            advanceBatch();
        }

        @Override
        public boolean hasNext() {
            ensureBatchAvailable();
            return batchIndex < batch.size();
        }

        @Override
        public ConfluencePageDto next() {
            ensureBatchAvailable();
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return batch.get(batchIndex++);
        }

        private void ensureBatchAvailable() {
            while (!endOfStream && batchIndex >= batch.size()) {
                advanceBatch();
            }
        }

        private void advanceBatch() {
            while (true) {
                if (endOfStream) {
                    batch = List.of();
                    batchIndex = 0;
                    return;
                }
                ConfluenceChildListResponse response = fetchChildBatch(pageId, cumulativeStart, nextUrl);
                List<ConfluencePageDto> results = response.getResults();
                if (results.isEmpty()) {
                    String nextHref = response.nextLink();
                    if (nextHref != null && !nextHref.isBlank()) {
                        nextUrl = absUrl(nextHref);
                        continue;
                    }
                    endOfStream = true;
                    batch = List.of();
                    batchIndex = 0;
                    return;
                }
                batch = results;
                batchIndex = 0;
                cumulativeStart += results.size();
                String nextHref = response.nextLink();
                if (nextHref != null && !nextHref.isBlank()) {
                    nextUrl = absUrl(nextHref);
                } else {
                    nextUrl = null;
                    if (results.size() < CHILD_PAGE_LIMIT) {
                        endOfStream = true;
                    }
                }
                return;
            }
        }
    }
}
