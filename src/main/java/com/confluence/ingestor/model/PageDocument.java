package com.confluence.ingestor.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Per-page document metadata persisted at {@code data/{parentPageId}/pages/{pageId}/metadata.json}.
 * Phase 3 will populate from Confluence REST {@code expand=body.storage,version,space,ancestors}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageDocument {

    private String pageId;
    private String title;
    private String spaceKey;
    private int version;
    private String webUrl;
    private String parentPageId;
    private Instant extractedAt;
    private String markdownPath;
    private String assetsDirectory;
    private Map<String, Object> ancestors;
    private List<PageAssetDocument> assets;
    private List<PageTableDocument> tables;
    private List<PageDiagramDocument> diagrams;

    public String getPageId() {
        return pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSpaceKey() {
        return spaceKey;
    }

    public void setSpaceKey(String spaceKey) {
        this.spaceKey = spaceKey;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public void setWebUrl(String webUrl) {
        this.webUrl = webUrl;
    }

    public String getParentPageId() {
        return parentPageId;
    }

    public void setParentPageId(String parentPageId) {
        this.parentPageId = parentPageId;
    }

    public Instant getExtractedAt() {
        return extractedAt;
    }

    public void setExtractedAt(Instant extractedAt) {
        this.extractedAt = extractedAt;
    }

    public String getMarkdownPath() {
        return markdownPath;
    }

    public void setMarkdownPath(String markdownPath) {
        this.markdownPath = markdownPath;
    }

    public String getAssetsDirectory() {
        return assetsDirectory;
    }

    public void setAssetsDirectory(String assetsDirectory) {
        this.assetsDirectory = assetsDirectory;
    }

    public Map<String, Object> getAncestors() {
        return ancestors;
    }

    public void setAncestors(Map<String, Object> ancestors) {
        this.ancestors = ancestors;
    }

    public List<PageAssetDocument> getAssets() {
        return assets;
    }

    public void setAssets(List<PageAssetDocument> assets) {
        this.assets = assets;
    }

    public List<PageTableDocument> getTables() {
        return tables;
    }

    public void setTables(List<PageTableDocument> tables) {
        this.tables = tables;
    }

    public List<PageDiagramDocument> getDiagrams() {
        return diagrams;
    }

    public void setDiagrams(List<PageDiagramDocument> diagrams) {
        this.diagrams = diagrams;
    }
}
