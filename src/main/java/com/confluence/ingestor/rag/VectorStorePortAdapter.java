package com.confluence.ingestor.rag;

import com.confluence.ingestor.model.ChunkDocument;
import com.confluence.ingestor.port.VectorStorePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnBean(ChromaIngestionService.class)
public class VectorStorePortAdapter implements VectorStorePort {

    private final ChromaIngestionService chromaIngestionService;

    public VectorStorePortAdapter(ChromaIngestionService chromaIngestionService) {
        this.chromaIngestionService = chromaIngestionService;
    }

    @Override
    public int ingestChunks(List<ChunkDocument> chunks) {
        return chromaIngestionService.ingestChunks(chunks);
    }
}
