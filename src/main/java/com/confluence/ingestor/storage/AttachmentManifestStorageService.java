package com.confluence.ingestor.storage;

import com.confluence.ingestor.model.AttachmentManifestEntry;
import com.confluence.ingestor.model.AttachmentsManifestDocument;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AttachmentManifestStorageService {

    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    public AttachmentManifestStorageService(FileStorageService fileStorageService, ObjectMapper objectMapper) {
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
    }

    public void writeManifest(String parentPageId, String pageId, AttachmentsManifestDocument manifest)
            throws IOException {
        Path path = fileStorageService.pageAttachmentsManifestPath(parentPageId, pageId);
        fileStorageService.writeJsonAtomic(path, manifest);
    }

    public AttachmentsManifestDocument readManifest(String parentPageId, String pageId) throws IOException {
        Path path = fileStorageService.pageAttachmentsManifestPath(parentPageId, pageId);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        return objectMapper.readValue(path.toFile(), AttachmentsManifestDocument.class);
    }

    public Map<String, AttachmentManifestEntry> readManifestIndex(String parentPageId, String pageId)
            throws IOException {
        AttachmentsManifestDocument manifest = readManifest(parentPageId, pageId);
        Map<String, AttachmentManifestEntry> index = new LinkedHashMap<>();
        if (manifest == null || manifest.getAttachments() == null) {
            return index;
        }
        for (AttachmentManifestEntry entry : manifest.getAttachments()) {
            if (entry.getRelativePath() != null) {
                index.put(entry.getRelativePath(), entry);
            }
        }
        return index;
    }
}
