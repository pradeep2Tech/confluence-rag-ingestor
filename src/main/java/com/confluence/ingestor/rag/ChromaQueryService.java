package com.confluence.ingestor.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

/**
 * Semantic search over ChromaDB via Spring AI {@link VectorStore}.
 */
public class ChromaQueryService {

    private static final Logger log = LoggerFactory.getLogger(ChromaQueryService.class);

    private final VectorStore vectorStore;

    public ChromaQueryService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Observed(name = "rag.chroma.query")
    public List<Document> search(String query, String parentPageId, int topK, double similarityThreshold) {
        log.debug(
                "Chroma search parentPageId={} topK={} threshold={} queryLength={}",
                parentPageId,
                topK,
                similarityThreshold,
                query != null ? query.length() : 0);
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold);

        if (parentPageId != null && !parentPageId.isBlank()) {
            builder.filterExpression(new FilterExpressionBuilder().eq("parentPageId", parentPageId).build());
        }

        List<Document> results = vectorStore.similaritySearch(builder.build());
        log.debug("Chroma search returned hits={}", results.size());
        return results;
    }
}
