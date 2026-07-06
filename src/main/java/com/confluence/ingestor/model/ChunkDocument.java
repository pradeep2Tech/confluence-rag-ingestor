package com.confluence.ingestor.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * One RAG chunk — Phase 7 will write collections as JSONL.
 * Phase 8 will embed and store in ChromaDB with the same metadata map.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChunkDocument {

    private String chunkId;
    private String pageId;
    private String parentPageId;
    private String headingPath;
    private int chunkIndex;
    private String text;
    private Map<String, Object> metadata;

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

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

    public String getHeadingPath() {
        return headingPath;
    }

    public void setHeadingPath(String headingPath) {
        this.headingPath = headingPath;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
