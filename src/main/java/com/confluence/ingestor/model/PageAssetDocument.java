package com.confluence.ingestor.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One downloaded asset recorded in {@link PageDocument#getAssets()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageAssetDocument {

    private String fileName;
    private String mediaType;
    private long sizeBytes;
    private String attachmentId;
    private String localPath;

    public static PageAssetDocument of(
            String fileName, String mediaType, long sizeBytes, String attachmentId, String localPath) {
        PageAssetDocument asset = new PageAssetDocument();
        asset.setFileName(fileName);
        asset.setMediaType(mediaType);
        asset.setSizeBytes(sizeBytes);
        asset.setAttachmentId(attachmentId);
        asset.setLocalPath(localPath);
        return asset;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }
}
