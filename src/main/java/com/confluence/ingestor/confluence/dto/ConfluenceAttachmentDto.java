package com.confluence.ingestor.confluence.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfluenceAttachmentDto {

    private String id;
    private String title;

    @JsonProperty("_links")
    private Map<String, String> links;

    private Map<String, Object> extensions;

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

    public Map<String, String> getLinks() {
        return links;
    }

    public void setLinks(Map<String, String> links) {
        this.links = links;
    }

    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions;
    }

    public String downloadPath() {
        if (links == null) {
            return null;
        }
        return links.get("download");
    }

    public String mediaType() {
        if (extensions == null) {
            return "application/octet-stream";
        }
        Object mediaType = extensions.get("mediaType");
        return mediaType != null ? mediaType.toString() : "application/octet-stream";
    }

    public long fileSize() {
        if (extensions == null) {
            return 0L;
        }
        Object fileSize = extensions.get("fileSize");
        if (fileSize instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
