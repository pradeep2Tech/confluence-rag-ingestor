package com.confluence.ingestor.config;

import com.confluence.ingestor.rag.ChromaIngestionService;
import com.confluence.ingestor.rag.ChromaQueryService;
import com.confluence.ingestor.rag.EmbeddingService;
import com.confluence.ingestor.rag.VectorStorePortAdapter;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers RAG beans after Spring AI auto-configuration.
 * {@code @ConditionalOnBean(VectorStore.class)} on {@code @Service} classes runs too early
 * (before {@link ChromaVectorStoreAutoConfiguration}), so those beans are defined here instead.
 */
@AutoConfiguration(
        after = {
            ChromaVectorStoreAutoConfiguration.class,
            OllamaEmbeddingAutoConfiguration.class
        })
public class IngestorRagAutoConfiguration {

    @Bean
    @ConditionalOnBean(ChromaVectorStore.class)
    public ChromaIngestionService chromaIngestionService(ChromaVectorStore vectorStore) {
        return new ChromaIngestionService(vectorStore);
    }

    @Bean
    @ConditionalOnBean(ChromaVectorStore.class)
    public ChromaQueryService chromaQueryService(ChromaVectorStore vectorStore) {
        return new ChromaQueryService(vectorStore);
    }

    @Bean
    @ConditionalOnBean(ChromaIngestionService.class)
    public VectorStorePortAdapter vectorStorePortAdapter(ChromaIngestionService chromaIngestionService) {
        return new VectorStorePortAdapter(chromaIngestionService);
    }

    @Bean
    @ConditionalOnBean(EmbeddingModel.class)
    public EmbeddingService embeddingService(EmbeddingModel embeddingModel) {
        return new EmbeddingService(embeddingModel);
    }
}
