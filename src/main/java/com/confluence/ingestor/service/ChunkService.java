package com.confluence.ingestor.service;

import com.confluence.ingestor.config.IngestorProperties;
import com.confluence.ingestor.model.AttachmentsManifestDocument;
import com.confluence.ingestor.model.ChunkDocument;
import com.confluence.ingestor.model.PageDocument;
import com.confluence.ingestor.model.PageManifestEntry;
import com.confluence.ingestor.rag.ImageChunkGenerator;
import com.confluence.ingestor.rag.MarkdownChunker;
import com.confluence.ingestor.storage.AttachmentManifestStorageService;
import com.confluence.ingestor.storage.ChunkStorageService;
import com.confluence.ingestor.storage.FileStorageService;
import com.confluence.ingestor.storage.ManifestService;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chunks a single on-disk page Markdown file into JSONL for RAG.
 */
@Service
public class ChunkService {

    private static final Logger log = LoggerFactory.getLogger(ChunkService.class);

    private final IngestorProperties properties;
    private final FileStorageService fileStorageService;
    private final MarkdownChunker markdownChunker;
    private final ImageChunkGenerator imageChunkGenerator;
    private final AttachmentManifestStorageService attachmentManifestStorageService;
    private final ChunkStorageService chunkStorageService;
    private final ManifestService manifestService;

    public ChunkService(
            IngestorProperties properties,
            FileStorageService fileStorageService,
            MarkdownChunker markdownChunker,
            ImageChunkGenerator imageChunkGenerator,
            AttachmentManifestStorageService attachmentManifestStorageService,
            ChunkStorageService chunkStorageService,
            ManifestService manifestService) {
        this.properties = properties;
        this.fileStorageService = fileStorageService;
        this.markdownChunker = markdownChunker;
        this.imageChunkGenerator = imageChunkGenerator;
        this.attachmentManifestStorageService = attachmentManifestStorageService;
        this.chunkStorageService = chunkStorageService;
        this.manifestService = manifestService;
    }

    @Observed(name = "ingestion.chunk.page")
    public ChunkResult chunkPage(String parentPageId, PageManifestEntry entry) {
        String pageId = entry.getPageId();
        try {
            var markdownPath = fileStorageService.resolvePageMarkdownPath(parentPageId, pageId);
            if (!Files.isRegularFile(markdownPath)) {
                return fail(parentPageId, pageId, "Markdown file not found: " + markdownPath);
            }

            String markdown = Files.readString(markdownPath);
            PageDocument pageDocument = loadPageDocument(parentPageId, pageId);
            Map<String, Object> metadata = buildChunkMetadata(entry, pageDocument);
            AttachmentsManifestDocument attachmentManifest =
                    attachmentManifestStorageService.readManifest(parentPageId, pageId);
            if (attachmentManifest != null) {
                metadata.put("attachmentCount", attachmentManifest.getAttachments() != null
                        ? attachmentManifest.getAttachments().size()
                        : 0);
            }
            List<ChunkDocument> chunks = markdownChunker.chunk(
                    markdown,
                    parentPageId,
                    pageId,
                    metadata,
                    properties.maxChunkCharacters());

            if (!containsInlineAttachmentInformation(markdown)) {
                List<ChunkDocument> imageChunks = imageChunkGenerator.generateImageChunks(
                        parentPageId, pageId, attachmentManifest, metadata, chunks.size());
                if (!imageChunks.isEmpty()) {
                    List<ChunkDocument> combined = new ArrayList<>(chunks);
                    combined.addAll(imageChunks);
                    chunks = combined;
                }
            }

            String chunksPath = chunkStorageService.writePageChunks(parentPageId, pageId, chunks);

            manifestService.mutateManifest(parentPageId, manifest -> {
                PageManifestEntry row = manifestService.findPageEntry(manifest, pageId);
                if (row != null) {
                    row.setChunked(true);
                    row.setChunksPath(chunksPath);
                    row.setLastError(null);
                    row.setNoOfRetries(0);
                }
            });

            log.info(
                    "Chunked page parentPageId={} pageId={} chunks={}",
                    parentPageId,
                    pageId,
                    chunks.size());
            return ChunkResult.success(pageId, chunks.size());
        } catch (Exception ex) {
            return fail(parentPageId, pageId, ex.getMessage());
        }
    }

    private PageDocument loadPageDocument(String parentPageId, String pageId) throws IOException {
        var metadataPath = fileStorageService.pageMetadataPath(parentPageId, pageId);
        if (!Files.isRegularFile(metadataPath)) {
            return null;
        }
        return fileStorageService.readJson(metadataPath, PageDocument.class);
    }

    private static Map<String, Object> buildChunkMetadata(PageManifestEntry entry, PageDocument pageDocument) {
        if (pageDocument != null) {
            return MarkdownChunker.pageMetadata(
                    pageDocument.getTitle(),
                    pageDocument.getWebUrl(),
                    pageDocument.getSpaceKey(),
                    pageDocument.getVersion());
        }
        return MarkdownChunker.pageMetadata(entry.getTitle(), entry.getWebUrl(), null, 0);
    }

    private static boolean containsInlineAttachmentInformation(String markdown) {
        return markdown != null && markdown.contains(MarkdownAttachmentEnrichmentService.ENRICHMENT_HEADING);
    }

    private ChunkResult fail(String parentPageId, String pageId, String message) {
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
                    "Could not update manifest chunk failure for parentPageId={} pageId={}: {}",
                    parentPageId,
                    pageId,
                    ex.getMessage());
        }
        log.warn("Chunking failed for parentPageId={} pageId={}: {}", parentPageId, pageId, error);
        return ChunkResult.failure(pageId, error);
    }

    public record ChunkResult(String pageId, boolean success, int chunkCount, String error) {
        public static ChunkResult success(String pageId, int chunkCount) {
            return new ChunkResult(pageId, true, chunkCount, null);
        }

        public static ChunkResult failure(String pageId, String error) {
            return new ChunkResult(pageId, false, 0, error);
        }
    }
}
