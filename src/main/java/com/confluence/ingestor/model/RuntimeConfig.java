package com.confluence.ingestor.model;

/**
 * In-memory runtime configuration for the dashboard UI.
 * PAT is never logged or returned unmasked from the API.
 */
public record RuntimeConfig(
        String llmProvider,
        String llmModel,
        String ollamaBaseUrl,
        String embeddingModel,
        String confluenceBaseUrl,
        String confluenceTarget,
        String parentPageId,
        String spaceKey,
        String vectorStore,
        String chromaHost,
        Integer chromaPort,
        String chromaCollectionName,
        boolean verifySsl,
        boolean patConfigured
) {
    public static RuntimeConfig defaults() {
        return new RuntimeConfig(
                "ollama",
                "llama3.2",
                "http://localhost:11434",
                "nomic-embed-text",
                "",
                "",
                "",
                null,
                "chroma",
                "http://localhost",
                8000,
                "confluence-rag",
                false,
                false);
    }

    public RuntimeConfig withPatConfigured(boolean configured) {
        return new RuntimeConfig(
                llmProvider,
                llmModel,
                ollamaBaseUrl,
                embeddingModel,
                confluenceBaseUrl,
                confluenceTarget,
                parentPageId,
                spaceKey,
                vectorStore,
                chromaHost,
                chromaPort,
                chromaCollectionName,
                verifySsl,
                configured);
    }
}
