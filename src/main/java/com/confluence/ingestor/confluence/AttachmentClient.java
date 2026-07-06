package com.confluence.ingestor.confluence;

import com.confluence.ingestor.confluence.dto.ConfluenceAttachmentDto;
import com.confluence.ingestor.confluence.dto.ConfluenceAttachmentListResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Confluence attachment listing and download — Bearer PAT via shared {@link RestClient}.
 */
public class AttachmentClient {

    private static final Logger log = LoggerFactory.getLogger(AttachmentClient.class);

    static final int ATTACHMENT_PAGE_LIMIT = 50;

    private final String baseUrl;
    private final RestClient restClient;

    public AttachmentClient(ConfluenceClient confluenceClient) {
        this.baseUrl = confluenceClient.baseUrl();
        this.restClient = confluenceClient.restClient();
    }

    public List<ConfluenceAttachmentDto> listAttachments(String pageId) {
        List<ConfluenceAttachmentDto> all = new ArrayList<>();
        int start = 0;
        String nextUrl = null;

        while (true) {
            ConfluenceAttachmentListResponse response = fetchAttachmentBatch(pageId, start, nextUrl);
            List<ConfluenceAttachmentDto> batch = response.getResults();
            if (batch.isEmpty()) {
                String nextHref = response.nextLink();
                if (nextHref != null && !nextHref.isBlank()) {
                    nextUrl = absUrl(nextHref);
                    continue;
                }
                break;
            }
            all.addAll(batch);
            String nextHref = response.nextLink();
            if (nextHref != null && !nextHref.isBlank()) {
                nextUrl = absUrl(nextHref);
                start += batch.size();
                continue;
            }
            nextUrl = null;
            if (batch.size() < ATTACHMENT_PAGE_LIMIT) {
                break;
            }
            start += batch.size();
        }

        log.debug("Listed {} attachments for pageId={}", all.size(), pageId);
        return all;
    }

    public byte[] downloadAttachment(String downloadPathOrUrl) {
        log.debug("Downloading attachment path={}", downloadPathOrUrl);
        try {
            String absolute = absUrl(downloadPathOrUrl);
            byte[] data = restClient.get()
                    .uri(URI.create(absolute))
                    .accept(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL)
                    .retrieve()
                    .body(byte[].class);
            log.debug("Downloaded attachment bytes={}", data != null ? data.length : 0);
            return data;
        } catch (RestClientResponseException ex) {
            log.warn("Attachment download failed status={} path={}", ex.getStatusCode().value(), downloadPathOrUrl);
            throw new ConfluenceClientError(
                    "Attachment download failed: HTTP " + ex.getStatusCode().value(),
                    ex.getStatusCode().value());
        } catch (Exception ex) {
            log.warn("Attachment download error path={}: {}", downloadPathOrUrl, ex.getMessage());
            throw new ConfluenceClientError("Attachment download failed: " + ex.getMessage());
        }
    }

    private ConfluenceAttachmentListResponse fetchAttachmentBatch(String pageId, int start, String nextUrl) {
        try {
            if (nextUrl != null && !nextUrl.isBlank()) {
                return restClient.get()
                        .uri(URI.create(absUrl(nextUrl)))
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(ConfluenceAttachmentListResponse.class);
            }
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/rest/api/content/{pageId}/child/attachment")
                            .queryParam("limit", ATTACHMENT_PAGE_LIMIT)
                            .queryParam("start", start)
                            .queryParam("expand", "extensions")
                            .build(pageId))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ConfluenceAttachmentListResponse.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return emptyAttachmentList();
            }
            throw toClientError("Attachment listing failed for pageId=" + pageId, ex);
        }
    }

    private static ConfluenceAttachmentListResponse emptyAttachmentList() {
        ConfluenceAttachmentListResponse empty = new ConfluenceAttachmentListResponse();
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
        String body = ex.getResponseBodyAsString();
        String snippet = body != null && body.length() > 500 ? body.substring(0, 500) : body;
        return new ConfluenceClientError(
                prefix + ": HTTP " + ex.getStatusCode().value() + " " + (snippet != null ? snippet : ""),
                ex.getStatusCode().value());
    }
}
