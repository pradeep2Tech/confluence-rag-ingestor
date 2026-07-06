package com.confluence.ingestor.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One row in {@link PageManifest#getPages()}.
 * <p>
 * Replaces Python PDF fields ({@code pdfDownload}, {@code pdfPath}, {@code pdfSource}) with
 * Markdown-ingestion fields. Retry/skip policy uses {@link #noOfRetries} (Phase 10).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageManifestEntry {

    private String pageId;
    private String title;
    private String webUrl;
    private boolean markdownExtracted;
    private String markdownPath;
    private String metadataPath;
    private String assetsDirectory;
    private boolean chunked;
    private String chunksPath;
    private boolean vectorIngested;
    private String vectorCollection;
    private String lastError;
    private int noOfRetries;

    public static PageManifestEntry empty(String pageId, String title, String webUrl) {
        PageManifestEntry entry = new PageManifestEntry();
        entry.setPageId(pageId);
        entry.setTitle(title);
        entry.setWebUrl(webUrl);
        entry.setMarkdownExtracted(false);
        entry.setNoOfRetries(0);
        return entry;
    }

    /**
     * Copies ingestion progress from an existing manifest row into a freshly crawled row.
     * Title and webUrl on {@code target} are left unchanged (caller sets from crawl).
     */
    public static void copyIngestionState(PageManifestEntry source, PageManifestEntry target) {
        target.setMarkdownExtracted(source.isMarkdownExtracted());
        target.setMarkdownPath(source.getMarkdownPath());
        target.setMetadataPath(source.getMetadataPath());
        target.setAssetsDirectory(source.getAssetsDirectory());
        target.setChunked(source.isChunked());
        target.setChunksPath(source.getChunksPath());
        target.setVectorIngested(source.isVectorIngested());
        target.setVectorCollection(source.getVectorCollection());
        target.setLastError(source.getLastError());
        target.setNoOfRetries(source.getNoOfRetries());
    }

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

    public String getWebUrl() {
        return webUrl;
    }

    public void setWebUrl(String webUrl) {
        this.webUrl = webUrl;
    }

    public boolean isMarkdownExtracted() {
        return markdownExtracted;
    }

    public void setMarkdownExtracted(boolean markdownExtracted) {
        this.markdownExtracted = markdownExtracted;
    }

    public String getMarkdownPath() {
        return markdownPath;
    }

    public void setMarkdownPath(String markdownPath) {
        this.markdownPath = markdownPath;
    }

    public String getMetadataPath() {
        return metadataPath;
    }

    public void setMetadataPath(String metadataPath) {
        this.metadataPath = metadataPath;
    }

    public String getAssetsDirectory() {
        return assetsDirectory;
    }

    public void setAssetsDirectory(String assetsDirectory) {
        this.assetsDirectory = assetsDirectory;
    }

    public boolean isChunked() {
        return chunked;
    }

    public void setChunked(boolean chunked) {
        this.chunked = chunked;
    }

    public String getChunksPath() {
        return chunksPath;
    }

    public void setChunksPath(String chunksPath) {
        this.chunksPath = chunksPath;
    }

    public boolean isVectorIngested() {
        return vectorIngested;
    }

    public void setVectorIngested(boolean vectorIngested) {
        this.vectorIngested = vectorIngested;
    }

    public String getVectorCollection() {
        return vectorCollection;
    }

    public void setVectorCollection(String vectorCollection) {
        this.vectorCollection = vectorCollection;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public int getNoOfRetries() {
        return noOfRetries;
    }

    public void setNoOfRetries(int noOfRetries) {
        this.noOfRetries = noOfRetries;
    }
}
