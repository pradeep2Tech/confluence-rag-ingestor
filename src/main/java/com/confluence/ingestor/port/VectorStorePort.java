package com.confluence.ingestor.port;

import com.confluence.ingestor.model.ChunkDocument;

import java.util.List;

/**
 * Vector store write/read boundary for embedding-indexer extraction.
 */
public interface VectorStorePort {

    int ingestChunks(List<ChunkDocument> chunks);
}
