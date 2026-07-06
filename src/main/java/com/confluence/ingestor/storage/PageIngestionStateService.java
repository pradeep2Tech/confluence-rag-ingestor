package com.confluence.ingestor.storage;

import com.confluence.ingestor.config.IngestorProperties;
import com.confluence.ingestor.model.PageIngestionState;
import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.model.PageManifestEntry;
import com.confluence.ingestor.port.ManifestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Writes per-page {@code ingestion-state.json} during batches and merges into manifest once per stage.
 */
@Service
public class PageIngestionStateService {

    private static final Logger log = LoggerFactory.getLogger(PageIngestionStateService.class);

    private final FileStorageService fileStorageService;
    private final ManifestRepository manifestRepository;
    private final IngestorProperties properties;

    public PageIngestionStateService(
            FileStorageService fileStorageService,
            ManifestRepository manifestRepository,
            IngestorProperties properties) {
        this.fileStorageService = fileStorageService;
        this.manifestRepository = manifestRepository;
        this.properties = properties;
    }

    public boolean isPerPageStateEnabled() {
        return properties.perPageStateEnabled();
    }

    public Path pageIngestionStatePath(String parentPageId, String pageId) {
        return fileStorageService.pageDirectory(parentPageId, pageId).resolve("ingestion-state.json");
    }

    public void recordPageState(String parentPageId, String pageId, String stage, Consumer<PageIngestionState> mutator)
            throws IOException {
        if (!isPerPageStateEnabled()) {
            return;
        }
        PageIngestionState state = loadOrNew(parentPageId, pageId);
        state.setStage(stage);
        mutator.accept(state);
        state.setUpdatedAt(Instant.now());
        fileStorageService.writeJsonAtomic(pageIngestionStatePath(parentPageId, pageId), state);
    }

    public void mergeAllIntoManifest(String parentPageId) throws IOException {
        if (!isPerPageStateEnabled()) {
            return;
        }
        Path pagesDir = fileStorageService.parentDataDir(parentPageId).resolve("pages");
        if (!Files.isDirectory(pagesDir)) {
            return;
        }
        try (Stream<Path> children = Files.list(pagesDir)) {
            boolean anyMerged = false;
            for (Path pageDir : children.filter(Files::isDirectory).toList()) {
                Path statePath = pageDir.resolve("ingestion-state.json");
                if (!Files.isRegularFile(statePath)) {
                    continue;
                }
                PageIngestionState state = fileStorageService.readJson(statePath, PageIngestionState.class);
                if (state.getPageId() == null) {
                    state.setPageId(pageDir.getFileName().toString());
                }
                final PageIngestionState snapshot = state;
                manifestRepository.mutateManifest(parentPageId, manifest -> {
                    PageManifestEntry row = manifestRepository.findPageEntry(manifest, snapshot.getPageId());
                    if (row != null) {
                        snapshot.applyTo(row);
                    }
                });
                anyMerged = true;
            }
            if (anyMerged) {
                log.info("Merged per-page ingestion-state files into manifest for parentPageId={}", parentPageId);
            }
        }
    }

    private PageIngestionState loadOrNew(String parentPageId, String pageId) throws IOException {
        Path path = pageIngestionStatePath(parentPageId, pageId);
        if (Files.isRegularFile(path)) {
            return fileStorageService.readJson(path, PageIngestionState.class);
        }
        PageIngestionState state = new PageIngestionState();
        state.setPageId(pageId);
        if (manifestRepository.manifestExists(parentPageId)) {
            PageManifest manifest = manifestRepository.loadManifest(parentPageId);
            PageManifestEntry entry = manifestRepository.findPageEntry(manifest, pageId);
            if (entry != null) {
                state.setMarkdownExtracted(entry.isMarkdownExtracted());
                state.setMarkdownPath(entry.getMarkdownPath());
                state.setMetadataPath(entry.getMetadataPath());
                state.setAssetsDirectory(entry.getAssetsDirectory());
                state.setChunked(entry.isChunked());
                state.setChunksPath(entry.getChunksPath());
                state.setVectorIngested(entry.isVectorIngested());
                state.setVectorCollection(entry.getVectorCollection());
                state.setLastError(entry.getLastError());
                state.setNoOfRetries(entry.getNoOfRetries());
            }
        }
        return state;
    }
}
