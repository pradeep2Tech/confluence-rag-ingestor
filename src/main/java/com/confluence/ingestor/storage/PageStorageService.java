package com.confluence.ingestor.storage;

import com.confluence.ingestor.confluence.dto.ConfluenceAncestor;
import com.confluence.ingestor.confluence.dto.ConfluencePageContentDto;
import com.confluence.ingestor.model.PageAssetDocument;
import com.confluence.ingestor.model.PageDiagramDocument;
import com.confluence.ingestor.model.PageTableDocument;
import com.confluence.ingestor.model.PageDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PageStorageService {

    private final FileStorageService fileStorageService;

    public PageStorageService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public PageArtifacts writePageArtifacts(
            String parentPageId,
            ConfluencePageContentDto page,
            String webUrl,
            String markdown,
            List<PageAssetDocument> assets,
            List<PageTableDocument> tables,
            List<PageDiagramDocument> diagrams) throws IOException {
        String pageId = page.getId();
        Files.createDirectories(fileStorageService.pageDirectory(parentPageId, pageId));
        Files.createDirectories(fileStorageService.pageAssetsDirectory(parentPageId, pageId));

        fileStorageService.writeTextAtomic(fileStorageService.pageMarkdownPath(parentPageId, pageId), markdown);

        PageDocument metadata = buildMetadata(parentPageId, page, webUrl, assets, tables, diagrams);
        fileStorageService.writeJsonAtomic(fileStorageService.pageMetadataPath(parentPageId, pageId), metadata);

        return new PageArtifacts(
                fileStorageService.displayPath(fileStorageService.pageMarkdownPath(parentPageId, pageId)),
                fileStorageService.displayPath(fileStorageService.pageMetadataPath(parentPageId, pageId)),
                fileStorageService.displayPath(fileStorageService.pageAssetsDirectory(parentPageId, pageId)));
    }

    private PageDocument buildMetadata(
            String parentPageId,
            ConfluencePageContentDto page,
            String webUrl,
            List<PageAssetDocument> assets,
            List<PageTableDocument> tables,
            List<PageDiagramDocument> diagrams) {
        PageDocument document = new PageDocument();
        document.setPageId(page.getId());
        document.setTitle(page.getTitle());
        document.setSpaceKey(page.spaceKey());
        document.setVersion(page.versionNumber());
        document.setWebUrl(webUrl);
        document.setParentPageId(parentPageId);
        document.setExtractedAt(Instant.now());
        document.setMarkdownPath(fileStorageService.displayPath(
                fileStorageService.pageMarkdownPath(parentPageId, page.getId())));
        document.setAssetsDirectory(fileStorageService.displayPath(
                fileStorageService.pageAssetsDirectory(parentPageId, page.getId())));
        document.setAncestors(toAncestorMap(page.getAncestors()));
        document.setAssets(assets != null && !assets.isEmpty() ? assets : null);
        document.setTables(tables != null && !tables.isEmpty() ? tables : null);
        document.setDiagrams(diagrams != null && !diagrams.isEmpty() ? diagrams : null);
        return document;
    }

    private static Map<String, Object> toAncestorMap(List<ConfluenceAncestor> ancestors) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", ancestors.stream()
                .map(ancestor -> Map.of(
                        "id", ancestor.getId() != null ? ancestor.getId() : "",
                        "title", ancestor.getTitle() != null ? ancestor.getTitle() : ""))
                .toList());
        return result;
    }

    public record PageArtifacts(String markdownPath, String metadataPath, String assetsDirectory) {
    }
}
