package com.confluence.ingestor.rag;

import com.confluence.ingestor.model.ChunkDocument;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads chunk documents into ChromaDB via Spring AI {@link VectorStore}.
 */
public class ChromaIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ChromaIngestionService.class);

    private final VectorStore vectorStore;

    public ChromaIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Observed(name = "rag.chroma.ingest")
    public int ingestChunks(List<ChunkDocument> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            log.debug("Chroma ingest skipped — empty chunk list");
            return 0;
        }
        log.info("Chroma ingest starting chunkCount={}", chunks.size());
        List<Document> documents = new ArrayList<>();
        for (ChunkDocument chunk : chunks) {
            documents.add(toDocument(chunk));
        }
        vectorStore.add(documents);
        log.info("Chroma ingest complete chunkCount={}", documents.size());
        return documents.size();
    }

    private static Document toDocument(ChunkDocument chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("chunkId", chunk.getChunkId());
        metadata.put("pageId", chunk.getPageId());
        metadata.put("parentPageId", chunk.getParentPageId());
        metadata.put("headingPath", chunk.getHeadingPath());
        metadata.put("chunkIndex", chunk.getChunkIndex());
        if (chunk.getChunkType() != null) {
            metadata.put("chunkType", chunk.getChunkType());
        }
        if (chunk.getAssetType() != null) {
            metadata.put("assetType", chunk.getAssetType());
        }
        if (chunk.getAssetPath() != null) {
            metadata.put("assetPath", chunk.getAssetPath());
        }
        if (chunk.getSemanticChunkType() != null) {
            metadata.put("semanticChunkType", chunk.getSemanticChunkType());
        }
        if (chunk.getMetadata() != null) {
            metadata.putAll(chunk.getMetadata());
        }
        return Document.builder()
                .id(chunk.getChunkId())
                .text(chunk.getText())
                .metadata(metadata)
                .build();
    }
}
