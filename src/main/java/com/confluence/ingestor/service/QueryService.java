package com.confluence.ingestor.service;

import com.confluence.ingestor.api.dto.QueryHit;
import com.confluence.ingestor.api.dto.QueryRequest;
import com.confluence.ingestor.api.dto.QueryResponse;
import com.confluence.ingestor.config.IngestorProperties;
import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.model.PageManifestEntry;
import com.confluence.ingestor.rag.ChromaQueryService;
import com.confluence.ingestor.storage.ManifestService;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG retrieval — semantic search over ingested chunks with Confluence source links.
 */
@Service
public class QueryService {

    private static final Logger log = LoggerFactory.getLogger(QueryService.class);

    private final IngestorProperties properties;
    private final ObjectProvider<ChromaQueryService> chromaQueryService;
    private final ManifestService manifestService;

    public QueryService(
            IngestorProperties properties,
            ObjectProvider<ChromaQueryService> chromaQueryService,
            ManifestService manifestService) {
        this.properties = properties;
        this.chromaQueryService = chromaQueryService;
        this.manifestService = manifestService;
    }

    @Observed(name = "query.search")
    public QueryResponse query(QueryRequest request) {
        String queryText = request.query();
        String parentPageId = request.normalizedParentPageId();

        if (!properties.queryEnabled()) {
            return QueryResponse.error(
                    queryText,
                    parentPageId,
                    "Query API disabled",
                    "Set confluence.ingestor.query-enabled=true");
        }

        ChromaQueryService searchService = chromaQueryService.getIfAvailable();
        if (searchService == null) {
            return QueryResponse.error(
                    queryText,
                    parentPageId,
                    "Vector store not configured",
                    "Start ChromaDB/Ollama and check spring.ai.* settings");
        }

        try {
            int topK = request.resolvedTopK(properties.defaultQueryTopK());
            double threshold = request.resolvedSimilarityThreshold(properties.defaultSimilarityThreshold());

            List<Document> documents = searchService.search(queryText, parentPageId, topK, threshold);
            Map<String, PageManifest> manifestCache = new HashMap<>();
            List<QueryHit> hits = new ArrayList<>();
            for (Document document : documents) {
                hits.add(toHit(document, manifestCache));
            }

            log.info(
                    "Query completed parentPageId={} topK={} hits={}",
                    parentPageId != null ? parentPageId : "*",
                    topK,
                    hits.size());

            return QueryResponse.success(
                    queryText,
                    parentPageId,
                    properties.chromaCollectionName(),
                    hits);
        } catch (Exception ex) {
            log.warn("Query failed for parentPageId={}: {}", parentPageId, ex.getMessage());
            return QueryResponse.error(
                    queryText,
                    parentPageId,
                    "Query failed",
                    ex.getMessage());
        }
    }

    private QueryHit toHit(Document document, Map<String, PageManifest> manifestCache) {
        Map<String, Object> metadata = document.getMetadata() != null ? document.getMetadata() : Map.of();

        String chunkId = stringMetadata(metadata, "chunkId", document.getId());
        String pageId = stringMetadata(metadata, "pageId", null);
        String parentPageId = stringMetadata(metadata, "parentPageId", null);
        String title = stringMetadata(metadata, "title", null);
        String webUrl = stringMetadata(metadata, "webUrl", null);
        String headingPath = stringMetadata(metadata, "headingPath", null);
        String spaceKey = stringMetadata(metadata, "spaceKey", null);
        Integer version = intMetadata(metadata, "version");

        if ((title == null || webUrl == null) && parentPageId != null && pageId != null) {
            PageManifestEntry entry = findManifestEntry(manifestCache, parentPageId, pageId);
            if (entry != null) {
                if (title == null) {
                    title = entry.getTitle();
                }
                if (webUrl == null) {
                    webUrl = entry.getWebUrl();
                }
            }
        }

        Double distance = doubleMetadata(metadata, "distance");
        Double score = doubleMetadata(metadata, "score");
        if (score == null && distance != null) {
            score = Math.max(0.0, 1.0 - distance);
        }

        return new QueryHit(
                chunkId,
                pageId,
                parentPageId,
                title,
                webUrl,
                headingPath,
                spaceKey,
                version,
                document.getText(),
                score,
                distance);
    }

    private PageManifestEntry findManifestEntry(
            Map<String, PageManifest> manifestCache, String parentPageId, String pageId) {
        PageManifest manifest = manifestCache.computeIfAbsent(parentPageId, id -> {
            try {
                if (!manifestService.manifestExists(id)) {
                    return null;
                }
                return manifestService.loadManifest(id);
            } catch (IOException ex) {
                log.debug("Could not load manifest for parentPageId={}: {}", id, ex.getMessage());
                return null;
            }
        });
        if (manifest == null) {
            return null;
        }
        return manifestService.findPageEntry(manifest, pageId);
    }

    private static String stringMetadata(Map<String, Object> metadata, String key, String fallback) {
        Object value = metadata.get(key);
        if (value == null) {
            return fallback;
        }
        String text = value.toString().strip();
        return text.isEmpty() ? fallback : text;
    }

    private static Integer intMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Double doubleMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
