package com.confluence.ingestor.rag;

import com.confluence.ingestor.attachment.AttachmentType;
import com.confluence.ingestor.attachment.ExtractionStatus;
import com.confluence.ingestor.attachment.SemanticChunkType;
import com.confluence.ingestor.model.AttachmentManifestEntry;
import com.confluence.ingestor.model.AttachmentsManifestDocument;
import com.confluence.ingestor.model.ChunkDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates IMAGE chunks and semantic sub-chunks from attachment manifest entries.
 */
@Component
public class ImageChunkGenerator {

    private static final String CHUNK_TYPE_IMAGE = "IMAGE";

    public List<ChunkDocument> generateImageChunks(
            String parentPageId,
            String pageId,
            AttachmentsManifestDocument manifest,
            Map<String, Object> pageMetadata,
            int startingChunkIndex) {
        if (manifest == null || manifest.getAttachments() == null || manifest.getAttachments().isEmpty()) {
            return List.of();
        }

        List<ChunkDocument> chunks = new ArrayList<>();
        int chunkIndex = startingChunkIndex;

        for (AttachmentManifestEntry attachment : manifest.getAttachments()) {
            if (attachment.getExtractionStatus() == ExtractionStatus.SKIPPED) {
                continue;
            }
            if (attachment.getSearchableSummary() == null || attachment.getSearchableSummary().isBlank()) {
                continue;
            }

            chunks.add(buildImageChunk(parentPageId, pageId, attachment, pageMetadata, chunkIndex++, null));

            if (isComplexDiagram(attachment.getDetectedType())) {
                List<ChunkDocument> semanticChunks =
                        buildSemanticChunks(parentPageId, pageId, attachment, pageMetadata, chunkIndex);
                chunks.addAll(semanticChunks);
                chunkIndex += semanticChunks.size();
            }
        }
        return chunks;
    }

    private List<ChunkDocument> buildSemanticChunks(
            String parentPageId,
            String pageId,
            AttachmentManifestEntry attachment,
            Map<String, Object> pageMetadata,
            int startIndex) {
        List<ChunkDocument> chunks = new ArrayList<>();
        int index = startIndex;
        Map<String, Object> extracted = attachment.getExtractedMetadata();
        if (extracted == null) {
            return chunks;
        }

        index = addSemanticChunk(chunks, parentPageId, pageId, attachment, pageMetadata, index, SemanticChunkType.SUMMARY,
                attachment.getSearchableSummary());
        index = addSemanticChunk(chunks, parentPageId, pageId, attachment, pageMetadata, index, SemanticChunkType.COMPONENTS,
                joinList(extracted.get("components"), extracted.get("labels")));
        index = addSemanticChunk(chunks, parentPageId, pageId, attachment, pageMetadata, index, SemanticChunkType.RELATIONSHIPS,
                joinList(extracted.get("relationships")));
        addSemanticChunk(chunks, parentPageId, pageId, attachment, pageMetadata, index, SemanticChunkType.FLOW,
                joinList(extracted.get("userActions"), extracted.get("flow")));
        return chunks;
    }

    private int addSemanticChunk(
            List<ChunkDocument> chunks,
            String parentPageId,
            String pageId,
            AttachmentManifestEntry attachment,
            Map<String, Object> pageMetadata,
            int chunkIndex,
            SemanticChunkType semanticType,
            String text) {
        if (text == null || text.isBlank()) {
            return chunkIndex;
        }
        chunks.add(buildImageChunk(parentPageId, pageId, attachment, pageMetadata, chunkIndex, semanticType));
        return chunkIndex + 1;
    }

    private ChunkDocument buildImageChunk(
            String parentPageId,
            String pageId,
            AttachmentManifestEntry attachment,
            Map<String, Object> pageMetadata,
            int chunkIndex,
            SemanticChunkType semanticType) {
        ChunkDocument chunk = new ChunkDocument();
        String semanticSuffix = semanticType != null ? "-" + semanticType.name() : "";
        chunk.setChunkId(pageId + "-img-" + chunkIndex + semanticSuffix);
        chunk.setChunkType(CHUNK_TYPE_IMAGE);
        chunk.setAssetType(attachment.getDetectedType() != null ? attachment.getDetectedType().name() : null);
        chunk.setAssetPath(attachment.getRelativePath());
        chunk.setSemanticChunkType(semanticType != null ? semanticType.name() : null);
        chunk.setPageId(pageId);
        chunk.setParentPageId(parentPageId);
        chunk.setHeadingPath(attachment.getHeadingPath());
        chunk.setChunkIndex(chunkIndex);
        chunk.setText(resolveChunkText(attachment, semanticType));

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (pageMetadata != null) {
            metadata.putAll(pageMetadata);
        }
        if (attachment.getVisionModel() != null) {
            metadata.put("visionModel", attachment.getVisionModel());
        }
        if (attachment.getConfidence() != null) {
            metadata.put("confidence", attachment.getConfidence());
        }
        if (attachment.getDetectionMethod() != null) {
            metadata.put("detectionMethod", attachment.getDetectionMethod().name());
        }
        if (attachment.getFileName() != null) {
            metadata.put("fileName", attachment.getFileName());
        }
        chunk.setMetadata(metadata);
        return chunk;
    }

    private static String resolveChunkText(AttachmentManifestEntry attachment, SemanticChunkType semanticType) {
        if (semanticType == null) {
            return attachment.getSearchableSummary();
        }
        Map<String, Object> extracted = attachment.getExtractedMetadata();
        if (extracted == null) {
            return attachment.getSearchableSummary();
        }
        return switch (semanticType) {
            case SUMMARY -> attachment.getSearchableSummary();
            case COMPONENTS -> joinList(extracted.get("components"), extracted.get("labels"));
            case RELATIONSHIPS -> joinList(extracted.get("relationships"));
            case FLOW -> joinList(extracted.get("userActions"), extracted.get("flow"));
        };
    }

    private static boolean isComplexDiagram(AttachmentType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case DRAWIO_DIAGRAM, DRAWIO_SOURCE, STATE_DIAGRAM, CLASS_DIAGRAM, ER_DIAGRAM, GENERIC_DIAGRAM,
                    ARCHITECTURE_DIAGRAM, FLOWCHART, SEQUENCE_DIAGRAM, SVG_DIAGRAM, VISIO_DIAGRAM ->
                true;
            default -> false;
        };
    }

    private static String joinList(Object... values) {
        List<String> parts = new ArrayList<>();
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null && !item.toString().isBlank()) {
                        parts.add(item.toString().strip());
                    }
                }
            } else if (!value.toString().isBlank()) {
                parts.add(value.toString().strip());
            }
        }
        return parts.isEmpty() ? null : String.join("; ", parts);
    }
}
