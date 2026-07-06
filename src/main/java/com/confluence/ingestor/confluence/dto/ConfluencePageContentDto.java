package com.confluence.ingestor.confluence.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Confluence page with {@code body.storage} for Markdown extraction (Phase 3).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfluencePageContentDto {

    private String id;
    private String title;
    private ConfluencePageBody body;
    private ConfluenceVersion version;
    private ConfluenceSpace space;
    private List<ConfluenceAncestor> ancestors = new ArrayList<>();

    @JsonProperty("_links")
    private Map<String, String> links;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ConfluencePageBody getBody() {
        return body;
    }

    public void setBody(ConfluencePageBody body) {
        this.body = body;
    }

    public ConfluenceVersion getVersion() {
        return version;
    }

    public void setVersion(ConfluenceVersion version) {
        this.version = version;
    }

    public ConfluenceSpace getSpace() {
        return space;
    }

    public void setSpace(ConfluenceSpace space) {
        this.space = space;
    }

    public List<ConfluenceAncestor> getAncestors() {
        return ancestors != null ? ancestors : List.of();
    }

    public void setAncestors(List<ConfluenceAncestor> ancestors) {
        this.ancestors = ancestors;
    }

    public Map<String, String> getLinks() {
        return links;
    }

    public void setLinks(Map<String, String> links) {
        this.links = links;
    }

    public String storageHtml() {
        return body != null ? body.storageHtml() : "";
    }

    public int versionNumber() {
        return version != null ? version.getNumber() : 0;
    }

    public String spaceKey() {
        return space != null && space.getKey() != null ? space.getKey() : "";
    }
}
