package com.confluence.ingestor.storage;

import com.confluence.ingestor.confluence.ConfluenceClient;
import com.confluence.ingestor.confluence.dto.ConfluencePageDto;
import com.confluence.ingestor.config.IngestorProperties;
import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.model.PageManifestEntry;
import com.confluence.ingestor.port.ManifestPolicy;
import com.confluence.ingestor.port.ManifestRepository;
import com.confluence.ingestor.port.ManifestSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Thread-safe manifest I/O implementing {@link ManifestRepository}.
 * Business rules delegated to {@link ManifestPolicy}; locking to {@link ManifestLockCoordinator}.
 */
@Service
public class ManifestService implements ManifestRepository {

    private static final Logger log = LoggerFactory.getLogger(ManifestService.class);

    private final FileStorageService fileStorageService;
    private final IngestorProperties properties;
    private final ManifestLockCoordinator lockCoordinator;
    private final ManifestPolicy manifestPolicy;

    public ManifestService(
            FileStorageService fileStorageService,
            IngestorProperties properties,
            ManifestLockCoordinator lockCoordinator,
            ManifestPolicy manifestPolicy) {
        this.fileStorageService = fileStorageService;
        this.properties = properties;
        this.lockCoordinator = lockCoordinator;
        this.manifestPolicy = manifestPolicy;
    }

    @Override
    public boolean manifestExists(String parentPageId) {
        return Files.isRegularFile(fileStorageService.manifestPath(parentPageId));
    }

    @Override
    public String manifestDisplayPath(String parentPageId) {
        return fileStorageService.displayPath(fileStorageService.manifestPath(parentPageId));
    }

    @Override
    public PageManifest loadManifest(String parentPageId) throws IOException {
        Path path = fileStorageService.manifestPath(parentPageId);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Manifest not found: " + path);
        }
        return fileStorageService.readJson(path, PageManifest.class);
    }

    @Override
    public boolean createEmptyManifestIfMissing(String baseUrl, String parentPageId) throws IOException {
        if (manifestExists(parentPageId)) {
            return false;
        }
        PageManifest manifest = newEmptyManifest(baseUrl, parentPageId);
        saveManifest(parentPageId, manifest);
        log.info("Created empty manifest for parentPageId={}", parentPageId);
        return true;
    }

    @Override
    public PageManifest newEmptyManifest(String baseUrl, String parentPageId) {
        Instant now = Instant.now();
        PageManifest manifest = new PageManifest();
        manifest.setManifestVersion(properties.manifestVersion());
        manifest.setBaseUrl(baseUrl);
        manifest.setParentPageId(parentPageId);
        manifest.setCreatedAt(now);
        manifest.setUpdatedAt(now);
        manifest.setTotalPages(0);
        manifest.setPages(new ArrayList<>());
        return manifest;
    }

    @Override
    public void saveManifest(String parentPageId, PageManifest manifest) throws IOException {
        ReentrantLock lock = lockCoordinator.lockFor(parentPageId);
        lock.lock();
        try {
            manifest.setUpdatedAt(Instant.now());
            if (manifest.getPages() != null) {
                manifest.setTotalPages(manifest.getPages().size());
            }
            fileStorageService.writeJsonAtomic(fileStorageService.manifestPath(parentPageId), manifest);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PageManifest mutateManifest(String parentPageId, Consumer<PageManifest> mutator) throws IOException {
        ReentrantLock lock = lockCoordinator.lockFor(parentPageId);
        lock.lock();
        try {
            PageManifest manifest = loadManifest(parentPageId);
            mutator.accept(manifest);
            manifest.setUpdatedAt(Instant.now());
            if (manifest.getPages() != null) {
                manifest.setTotalPages(manifest.getPages().size());
            }
            fileStorageService.writeJsonAtomic(fileStorageService.manifestPath(parentPageId), manifest);
            return manifest;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PageManifestEntry findPageEntry(PageManifest manifest, String pageId) {
        if (manifest.getPages() == null) {
            return null;
        }
        for (PageManifestEntry entry : manifest.getPages()) {
            if (pageId.equals(entry.getPageId())) {
                return entry;
            }
        }
        return null;
    }

    public ManifestSummary summarize(PageManifest manifest) {
        return manifestPolicy.summarize(manifest);
    }

    public int effectiveRetryCount(PageManifestEntry page) {
        return manifestPolicy.effectiveRetryCount(page);
    }

    public boolean isPendingForIngestion(PageManifestEntry page) {
        return manifestPolicy.isPendingForIngestion(page);
    }

    public boolean isPendingForChunking(PageManifestEntry page) {
        return manifestPolicy.isPendingForChunking(page);
    }

    public boolean isPendingForVectorIngest(PageManifestEntry page) {
        return manifestPolicy.isPendingForVectorIngest(page);
    }

    public PageManifest buildFullManifest(
            String baseUrl,
            String parentPageId,
            ConfluencePageDto parentDoc,
            List<ConfluencePageDto> descendantDocs,
            ConfluenceClient client) throws IOException {
        Instant now = Instant.now();
        Instant createdAt = now;
        Map<String, PageManifestEntry> existingByPageId = new HashMap<>();
        if (manifestExists(parentPageId)) {
            PageManifest existing = loadManifest(parentPageId);
            if (existing.getCreatedAt() != null) {
                createdAt = existing.getCreatedAt();
            }
            if (existing.getPages() != null) {
                for (PageManifestEntry row : existing.getPages()) {
                    if (row.getPageId() != null) {
                        existingByPageId.put(row.getPageId(), row);
                    }
                }
            }
        }

        List<PageManifestEntry> pages = new ArrayList<>();
        String parentId = parentDoc.getId() != null ? parentDoc.getId() : parentPageId;
        String parentTitle = parentDoc.getTitle() != null ? parentDoc.getTitle() : "";
        pages.add(mergeCrawledEntry(
                parentId,
                parentTitle,
                client.buildWebUrl(parentDoc),
                existingByPageId.get(parentId)));

        Set<String> seen = new HashSet<>();
        seen.add(parentId);
        for (ConfluencePageDto descendant : descendantDocs) {
            String pageId = descendant.getId();
            if (pageId == null || pageId.isBlank() || seen.contains(pageId)) {
                continue;
            }
            seen.add(pageId);
            String title = descendant.getTitle() != null ? descendant.getTitle() : "";
            pages.add(mergeCrawledEntry(
                    pageId, title, client.buildWebUrl(descendant), existingByPageId.get(pageId)));
        }

        PageManifest manifest = new PageManifest();
        manifest.setManifestVersion(properties.manifestVersion());
        manifest.setBaseUrl(baseUrl);
        manifest.setParentPageId(parentPageId);
        manifest.setCreatedAt(createdAt);
        manifest.setUpdatedAt(now);
        manifest.setTotalPages(pages.size());
        manifest.setPages(pages);
        return manifest;
    }

    private static PageManifestEntry mergeCrawledEntry(
            String pageId, String title, String webUrl, PageManifestEntry existing) {
        PageManifestEntry entry = PageManifestEntry.empty(pageId, title, webUrl);
        if (existing != null) {
            PageManifestEntry.copyIngestionState(existing, entry);
        }
        return entry;
    }
}
