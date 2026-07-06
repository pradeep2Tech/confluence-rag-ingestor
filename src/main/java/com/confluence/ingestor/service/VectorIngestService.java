package com.confluence.ingestor.service;

import com.confluence.ingestor.config.IngestorProperties;
import com.confluence.ingestor.model.ChunkDocument;
import com.confluence.ingestor.model.PageManifestEntry;
import com.confluence.ingestor.rag.ChromaIngestionService;
import com.confluence.ingestor.storage.ChunkStorageService;
import com.confluence.ingestor.storage.ManifestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Ingests on-disk chunk JSONL for a single page into ChromaDB.
 */
@Service
public class VectorIngestService {

    private static final Logger log = LoggerFactory.getLogger(VectorIngestService.class);

    private final IngestorProperties properties;
    private final ChunkStorageService chunkStorageService;
    private final ManifestService manifestService;
    private final ObjectProvider<ChromaIngestionService> chromaIngestionService;

    public VectorIngestService(
            IngestorProperties properties,
            ChunkStorageService chunkStorageService,
            ManifestService manifestService,
            ObjectProvider<ChromaIngestionService> chromaIngestionService) {
        this.properties = properties;
        this.chunkStorageService = chunkStorageService;
        this.manifestService = manifestService;
        this.chromaIngestionService = chromaIngestionService;
    }

    @Observed(name = "ingestion.vector.page")
    public VectorIngestResult ingestPage(String parentPageId, PageManifestEntry entry) {
        String pageId = entry.getPageId();
        try {
            if (!properties.vectorIngestEnabled()) {
                return fail(parentPageId, pageId, "Vector ingest is disabled (confluence.ingestor.vector-ingest-enabled=false)");
            }

            ChromaIngestionService ingestionService = chromaIngestionService.getIfAvailable();
            if (ingestionService == null) {
                return fail(
                        parentPageId,
                        pageId,
                        "Chroma VectorStore is not configured — start ChromaDB/Ollama and check spring.ai.* settings");
            }

            List<ChunkDocument> chunks = chunkStorageService.readPageChunks(parentPageId, pageId);
            if (chunks.isEmpty()) {
                return fail(parentPageId, pageId, "Chunk JSONL not found or empty for pageId=" + pageId);
            }

            int ingested = ingestionService.ingestChunks(chunks);
            String collectionName = properties.chromaCollectionName();

            manifestService.mutateManifest(parentPageId, manifest -> {
                PageManifestEntry row = manifestService.findPageEntry(manifest, pageId);
                if (row != null) {
                    row.setVectorIngested(true);
                    row.setVectorCollection(collectionName);
                    row.setLastError(null);
                    row.setNoOfRetries(0);
                }
            });

            log.info(
                    "Vector ingest complete parentPageId={} pageId={} chunks={} collection={}",
                    parentPageId,
                    pageId,
                    ingested,
                    collectionName);
            return VectorIngestResult.success(pageId, ingested);
        } catch (Exception ex) {
            return fail(parentPageId, pageId, ex.getMessage());
        }
    }

    private VectorIngestResult fail(String parentPageId, String pageId, String message) {
        String error = message != null && message.length() > 2000 ? message.substring(0, 2000) : message;
        try {
            manifestService.mutateManifest(parentPageId, manifest -> {
                PageManifestEntry row = manifestService.findPageEntry(manifest, pageId);
                if (row != null) {
                    row.setLastError(error);
                    row.setNoOfRetries(row.getNoOfRetries() + 1);
                }
            });
        } catch (Exception ex) {
            log.warn(
                    "Could not update manifest vector ingest failure for parentPageId={} pageId={}: {}",
                    parentPageId,
                    pageId,
                    ex.getMessage());
        }
        log.warn("Vector ingest failed for parentPageId={} pageId={}: {}", parentPageId, pageId, error);
        return VectorIngestResult.failure(pageId, error);
    }

    public record VectorIngestResult(String pageId, boolean success, int chunkCount, String error) {
        public static VectorIngestResult success(String pageId, int chunkCount) {
            return new VectorIngestResult(pageId, true, chunkCount, null);
        }

        public static VectorIngestResult failure(String pageId, String error) {
            return new VectorIngestResult(pageId, false, 0, error);
        }
    }
}
