package com.confluence.ingestor.support;

import com.confluence.ingestor.rag.ChromaIngestionService;
import com.confluence.ingestor.rag.ChromaQueryService;
import org.springframework.ai.chat.model.ChatModel;
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
    ChromaIngestionService chromaIngestionService(VectorStore vectorStore) {
        return new ChromaIngestionService(vectorStore);
    }

    @Bean
    ChromaQueryService chromaQueryService(VectorStore vectorStore) {
        return new ChromaQueryService(vectorStore);
    }

    @Bean
    @Primary
    EmbeddingModel testEmbeddingModel() {
        return mock(EmbeddingModel.class);
    }

    @Bean
    @Primary
    ChatModel testChatModel() {
        return mock(ChatModel.class);
    }
}
