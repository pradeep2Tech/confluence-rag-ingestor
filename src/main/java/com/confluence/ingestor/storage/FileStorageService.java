package com.confluence.ingestor.storage;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.confluence.ingestor.config.IngestorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves on-disk paths under {@code data/{parentPageId}/} and performs atomic writes.
 * Windows file-lock retries follow the Python POC pattern (Phase 10 will extend usage).
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final int ATOMIC_REPLACE_MAX_ATTEMPTS = 15;

    private final IngestorProperties properties;
    private final ObjectMapper objectMapper;
    private final Path dataRoot;

    public FileStorageService(IngestorProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.dataRoot = resolveDataRoot(properties.dataDirectory());
    }

    public Path dataRoot() {
        return dataRoot;
    }

    public Path parentDataDir(String parentPageId) {
        return dataRoot.resolve(parentPageId);
    }

    public Path manifestPath(String parentPageId) {
        return parentDataDir(parentPageId).resolve("manifest.json");
    }

    public Path manifestTempPath(String parentPageId) {
        return parentDataDir(parentPageId).resolve("manifest.tmp.json");
    }

    public Path crawlProgressPath(String parentPageId) {
        return parentDataDir(parentPageId).resolve("crawl-progress.json");
    }

    public Path batchProgressPath(String parentPageId) {
        return parentDataDir(parentPageId).resolve("batch-progress.json");
    }

    public Path pageDirectory(String parentPageId, String pageId) {
        return parentDataDir(parentPageId).resolve("pages").resolve(pageId);
    }

    public Path pageMarkdownPath(String parentPageId, String pageId) {
        return pageDirectory(parentPageId, pageId).resolve("page.md");
    }

    public Path pageMetadataPath(String parentPageId, String pageId) {
        return pageDirectory(parentPageId, pageId).resolve("metadata.json");
    }

    public Path pageAssetsDirectory(String parentPageId, String pageId) {
        return pageDirectory(parentPageId, pageId).resolve("assets");
    }

    public Path pageTablesDirectory(String parentPageId, String pageId) {
        return pageAssetsDirectory(parentPageId, pageId).resolve("tables");
    }

    public Path pageTablePath(String parentPageId, String pageId, String fileName) {
        return pageTablesDirectory(parentPageId, pageId).resolve(fileName);
    }

    public Path pageDiagramsDirectory(String parentPageId, String pageId) {
        return pageAssetsDirectory(parentPageId, pageId).resolve("diagrams");
    }

    public Path pageDiagramPath(String parentPageId, String pageId, String fileName) {
        return pageDiagramsDirectory(parentPageId, pageId).resolve(fileName);
    }

    public Path chunksDirectory(String parentPageId) {
        return parentDataDir(parentPageId).resolve("chunks");
    }

    public Path pageChunksPath(String parentPageId, String pageId) {
        return chunksDirectory(parentPageId).resolve(pageId + ".jsonl");
    }

    /**
     * Creates {@code data/{parentPageId}/} and {@code pages/} scaffold. Idempotent.
     */
    public void ensureParentDataLayout(String parentPageId) throws IOException {
        Files.createDirectories(parentDataDir(parentPageId));
        Files.createDirectories(parentDataDir(parentPageId).resolve("pages"));
        Files.createDirectories(chunksDirectory(parentPageId));
    }

    public String displayPath(Path absolutePath) {
        Path normalized = absolutePath.normalize();
        try {
            return dataRoot.getParent() != null
                    ? dataRoot.getParent().relativize(normalized).toString().replace('\\', '/')
                    : normalized.toString().replace('\\', '/');
        } catch (IllegalArgumentException ex) {
            return normalized.toString().replace('\\', '/');
        }
    }

    public void writeJsonAtomic(Path targetPath, Object value) throws IOException {
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String fileName = targetPath.getFileName().toString();
        Path tempPath = parent != null
                ? parent.resolve(fileName + ".tmp")
                : Paths.get(fileName + ".tmp");

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), value);
        atomicReplace(tempPath, targetPath);
    }

    public <T> T readJson(Path path, Class<T> type) throws IOException {
        return objectMapper.readValue(path.toFile(), type);
    }

    public Optional<Map<String, Object>> readJsonMapIfExists(Path path) {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(path.toFile(), new TypeReference<>() {});
            return Optional.of(map);
        } catch (Exception ex) {
            log.warn("Failed to read JSON map from {}: {}", path, ex.getMessage());
            return Optional.empty();
        }
    }

    public void writeTextAtomic(Path targetPath, String content) throws IOException {
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempPath = parent != null
                ? parent.resolve(targetPath.getFileName() + ".tmp")
                : Paths.get(targetPath.getFileName() + ".tmp");
        Files.writeString(tempPath, content, StandardCharsets.UTF_8);
        atomicReplace(tempPath, targetPath);
    }

    public void writeBytesAtomic(Path targetPath, byte[] content) throws IOException {
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempPath = parent != null
                ? parent.resolve(targetPath.getFileName() + ".tmp")
                : Paths.get(targetPath.getFileName() + ".tmp");
        Files.write(tempPath, content);
        atomicReplace(tempPath, targetPath);
    }

    private void atomicReplace(Path tempPath, Path finalPath) throws IOException {
        IOException lastError = null;
        for (int attempt = 0; attempt < ATOMIC_REPLACE_MAX_ATTEMPTS; attempt++) {
            try {
                Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (AtomicMoveNotSupportedException ex) {
                try {
                    Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
                    return;
                } catch (IOException moveEx) {
                    lastError = moveEx;
                }
            } catch (IOException ex) {
                lastError = ex;
                if (!isTransientFileLockError(ex) || attempt == ATOMIC_REPLACE_MAX_ATTEMPTS - 1) {
                    throw ex;
                }
                if (attempt == 0) {
                    log.warn(
                            "Transient file lock replacing {} -> {} ({}). Retrying; close {} in editors if this persists.",
                            tempPath,
                            finalPath,
                            ex.getMessage(),
                            finalPath.getFileName());
                }
                sleepQuietly(40L * (attempt + 1));
            }
        }
        if (lastError != null) {
            throw lastError;
        }
    }

    private static boolean isTransientFileLockError(IOException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("access is denied")
                || lower.contains("being used by another process")
                || lower.contains("sharing violation");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static Path resolveDataRoot(String configured) {
        Path path = Paths.get(configured);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
    }
}
