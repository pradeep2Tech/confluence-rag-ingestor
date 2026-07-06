package com.confluence.ingestor.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Root manifest persisted at {@code data/{parentPageId}/manifest.json}.
 * Schema aligned with Python POC retry/progress concepts, adapted for Markdown ingestion.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageManifest {

    public static final int CURRENT_MANIFEST_VERSION = 1;

    private int manifestVersion = CURRENT_MANIFEST_VERSION;
    private String baseUrl;
    private String parentPageId;
    private Instant createdAt;
    private Instant updatedAt;
    private int totalPages;
    private List<PageManifestEntry> pages = new ArrayList<>();

    public int getManifestVersion() {
        return manifestVersion;
    }

    public void setManifestVersion(int manifestVersion) {
        this.manifestVersion = manifestVersion;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getParentPageId() {
        return parentPageId;
    }

    public void setParentPageId(String parentPageId) {
        this.parentPageId = parentPageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public List<PageManifestEntry> getPages() {
        return pages;
    }

    public void setPages(List<PageManifestEntry> pages) {
        this.pages = pages != null ? pages : new ArrayList<>();
    }
}
