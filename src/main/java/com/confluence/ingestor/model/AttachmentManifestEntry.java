package com.confluence.ingestor.model;

import com.confluence.ingestor.attachment.AttachmentType;
import com.confluence.ingestor.attachment.DetectionMethod;
import com.confluence.ingestor.attachment.ExtractionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttachmentManifestEntry {

    private String attachmentId;
    private String pageId;
    private String fileName;
    private String relativePath;
    private String mimeType;
    private String checksum;
    private AttachmentType detectedType;
    private DetectionMethod detectionMethod;
    private String headingPath;
    private ExtractionStatus extractionStatus;
    private String searchableSummary;
    private Double confidence;
    private String visionModel;
    private Instant analyzedAt;
    private int schemaVersion;
    private Map<String, Object> extractedMetadata;
    private String errorMessage;

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getPageId() {
        return pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public AttachmentType getDetectedType() {
        return detectedType;
    }

    public void setDetectedType(AttachmentType detectedType) {
        this.detectedType = detectedType;
    }

    public DetectionMethod getDetectionMethod() {
        return detectionMethod;
    }

    public void setDetectionMethod(DetectionMethod detectionMethod) {
        this.detectionMethod = detectionMethod;
    }

    public String getHeadingPath() {
        return headingPath;
    }

    public void setHeadingPath(String headingPath) {
        this.headingPath = headingPath;
    }

    public ExtractionStatus getExtractionStatus() {
        return extractionStatus;
    }

    public void setExtractionStatus(ExtractionStatus extractionStatus) {
        this.extractionStatus = extractionStatus;
    }

    public String getSearchableSummary() {
        return searchableSummary;
    }

    public void setSearchableSummary(String searchableSummary) {
        this.searchableSummary = searchableSummary;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getVisionModel() {
        return visionModel;
    }

    public void setVisionModel(String visionModel) {
        this.visionModel = visionModel;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(Instant analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public Map<String, Object> getExtractedMetadata() {
        return extractedMetadata;
    }

    public void setExtractedMetadata(Map<String, Object> extractedMetadata) {
        this.extractedMetadata = extractedMetadata;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
