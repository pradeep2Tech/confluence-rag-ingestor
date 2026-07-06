package com.confluence.ingestor.support;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * Replaces Chroma/Ollama beans in integration tests — no external AI services required.
 */
@TestConfiguration
public class TestAiConfiguration {

    @Bean
    @Primary
    VectorStore testVectorStore() {
        return mock(VectorStore.class);
    }

    @Bean
    @Primary
    EmbeddingModel testEmbeddingModel() {
        return mock(EmbeddingModel.class);
    }
}
