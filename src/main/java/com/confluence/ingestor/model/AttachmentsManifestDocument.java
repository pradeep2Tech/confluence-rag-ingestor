package com.confluence.ingestor.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttachmentsManifestDocument {

    private String pageId;
    private String parentPageId;
    private Instant generatedAt;
    private List<AttachmentManifestEntry> attachments;

    public String getPageId() {
        return pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public String getParentPageId() {
        return parentPageId;
    }

    public void setParentPageId(String parentPageId) {
        this.parentPageId = parentPageId;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public List<AttachmentManifestEntry> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AttachmentManifestEntry> attachments) {
        this.attachments = attachments;
    }
}
