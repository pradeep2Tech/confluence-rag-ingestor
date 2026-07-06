package com.confluence.ingestor.storage;

import tools.jackson.databind.ObjectMapper;
import com.confluence.ingestor.model.ChunkDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists per-page chunk collections as JSONL under {@code data/{parentPageId}/chunks/}.
 */
@Service
public class ChunkStorageService {

    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    public ChunkStorageService(FileStorageService fileStorageService, ObjectMapper objectMapper) {
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
    }

    public String writePageChunks(String parentPageId, String pageId, List<ChunkDocument> chunks)
            throws IOException {
        Files.createDirectories(fileStorageService.chunksDirectory(parentPageId));
        Path targetPath = fileStorageService.pageChunksPath(parentPageId, pageId);

        StringBuilder builder = new StringBuilder();
        for (ChunkDocument chunk : chunks) {
            builder.append(objectMapper.writeValueAsString(chunk)).append('\n');
        }
        fileStorageService.writeTextAtomic(targetPath, builder.toString());
        return fileStorageService.displayPath(targetPath);
    }

    public List<ChunkDocument> readPageChunks(String parentPageId, String pageId) throws IOException {
        Path targetPath = fileStorageService.pageChunksPath(parentPageId, pageId);
        if (!Files.isRegularFile(targetPath)) {
            return List.of();
        }
        List<ChunkDocument> chunks = new ArrayList<>();
        try (var lines = Files.lines(targetPath)) {
            lines.filter(line -> !line.isBlank())
                    .forEach(line -> {
                        try {
                            chunks.add(objectMapper.readValue(line, ChunkDocument.class));
                        } catch (Exception ex) {
                            throw new IllegalStateException("Failed to parse chunk JSONL line: " + ex.getMessage(), ex);
                        }
                    });
        }
        return chunks;
    }
}
