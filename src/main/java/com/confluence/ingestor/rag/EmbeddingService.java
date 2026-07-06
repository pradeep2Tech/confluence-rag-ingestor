package com.confluence.ingestor.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Thin wrapper over Spring AI {@link EmbeddingModel} for explicit embedding calls (Phase 9 query).
 */
@Service
@ConditionalOnBean(EmbeddingModel.class)
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Observed(name = "rag.embed")
    public float[] embed(String text) {
        log.debug("Embedding single text length={}", text != null ? text.length() : 0);
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of(text), null));
        return response.getResult().getOutput();
    }

    @Observed(name = "rag.embed.batch")
    public List<float[]> embedAll(List<String> texts) {
        log.debug("Embedding batch size={}", texts != null ? texts.size() : 0);
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(texts, null));
        return response.getResults().stream().map(result -> result.getOutput()).toList();
    }
}
