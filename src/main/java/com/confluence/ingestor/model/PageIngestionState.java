package com.confluence.ingestor.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Per-page ingestion progress written to {@code pages/{pageId}/ingestion-state.json}
 * to reduce manifest.json rewrite contention during batch runs (Phase 11c option B).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageIngestionState {

    private String pageId;
    private String stage;
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
    private Instant updatedAt;

    public String getPageId() {
        return pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void applyTo(PageManifestEntry entry) {
        entry.setMarkdownExtracted(markdownExtracted);
        entry.setMarkdownPath(markdownPath);
        entry.setMetadataPath(metadataPath);
        entry.setAssetsDirectory(assetsDirectory);
        entry.setChunked(chunked);
        entry.setChunksPath(chunksPath);
        entry.setVectorIngested(vectorIngested);
        entry.setVectorCollection(vectorCollection);
        entry.setLastError(lastError);
        entry.setNoOfRetries(noOfRetries);
    }
}
