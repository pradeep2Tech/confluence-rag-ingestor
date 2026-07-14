package com.confluence.ingestor.service;

import com.confluence.ingestor.attachment.AttachmentChecksumService;
import com.confluence.ingestor.attachment.AttachmentDetectionResult;
import com.confluence.ingestor.attachment.AttachmentInspector;
import com.confluence.ingestor.attachment.AttachmentType;
import com.confluence.ingestor.attachment.DetectionMethod;
import com.confluence.ingestor.attachment.ExtractionStatus;
import com.confluence.ingestor.attachment.MarkdownHeadingPathResolver;
import com.confluence.ingestor.attachment.OllamaVisionService;
import com.confluence.ingestor.attachment.VisionAnalysisResult;
import com.confluence.ingestor.config.AttachmentAnalysisProperties;
import com.confluence.ingestor.model.AttachmentManifestEntry;
import com.confluence.ingestor.model.AttachmentsManifestDocument;
import com.confluence.ingestor.model.PageAssetDocument;
import com.confluence.ingestor.storage.AttachmentManifestStorageService;
import com.confluence.ingestor.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Inspects, classifies, and extracts metadata for page attachments during transform.
 */
@Service
public class AttachmentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentAnalysisService.class);

    private final AttachmentAnalysisProperties properties;
    private final FileStorageService fileStorageService;
    private final AttachmentManifestStorageService manifestStorageService;
    private final AttachmentInspector inspector;
    private final AttachmentChecksumService checksumService;
    private final OllamaVisionService visionService;
    private final MarkdownHeadingPathResolver headingPathResolver;

    public AttachmentAnalysisService(
            AttachmentAnalysisProperties properties,
            FileStorageService fileStorageService,
            AttachmentManifestStorageService manifestStorageService,
            AttachmentInspector inspector,
            AttachmentChecksumService checksumService,
            OllamaVisionService visionService,
            MarkdownHeadingPathResolver headingPathResolver) {
        this.properties = properties;
        this.fileStorageService = fileStorageService;
        this.manifestStorageService = manifestStorageService;
        this.inspector = inspector;
        this.checksumService = checksumService;
        this.visionService = visionService;
        this.headingPathResolver = headingPathResolver;
    }

    public AttachmentsManifestDocument analyzePageAttachments(
            String parentPageId,
            String pageId,
            String markdown,
            List<PageAssetDocument> downloadedAssets) {
        if (!properties.enabled()) {
            return null;
        }

        long startNanos = System.nanoTime();
        Map<String, String> headingPaths = headingPathResolver.resolveHeadingPaths(markdown);
        Map<String, AttachmentManifestEntry> previousByPath = loadPreviousEntries(parentPageId, pageId);
        Map<String, PageAssetDocument> assetIndex = indexAssets(downloadedAssets);

        List<AttachmentManifestEntry> entries = new ArrayList<>();
        try (Stream<Path> files = listAnalyzableFiles(parentPageId, pageId)) {
            files.forEach(file -> {
                AttachmentManifestEntry entry = analyzeFile(
                        parentPageId, pageId, file, headingPaths, previousByPath, assetIndex);
                if (entry != null) {
                    entries.add(entry);
                }
            });
        } catch (IOException ex) {
            log.warn(
                    "Attachment analysis listing failed parentPageId={} pageId={}: {}",
                    parentPageId,
                    pageId,
                    ex.getMessage());
        }

        AttachmentsManifestDocument manifest = new AttachmentsManifestDocument();
        manifest.setPageId(pageId);
        manifest.setParentPageId(parentPageId);
        manifest.setGeneratedAt(Instant.now());
        manifest.setAttachments(entries);

        try {
            manifestStorageService.writeManifest(parentPageId, pageId, manifest);
        } catch (IOException ex) {
            log.warn(
                    "Failed to write attachments manifest parentPageId={} pageId={}: {}",
                    parentPageId,
                    pageId,
                    ex.getMessage());
        }

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info(
                "Attachment analysis complete parentPageId={} pageId={} attachments={} processingTimeMs={}",
                parentPageId,
                pageId,
                entries.size(),
                elapsedMs);
        return manifest;
    }

    private AttachmentManifestEntry analyzeFile(
            String parentPageId,
            String pageId,
            Path file,
            Map<String, String> headingPaths,
            Map<String, AttachmentManifestEntry> previousByPath,
            Map<String, PageAssetDocument> assetIndex) {
        long startNanos = System.nanoTime();
        String fileName = file.getFileName().toString();
        String relativePath = toRelativeAssetPath(parentPageId, pageId, file);
        PageAssetDocument assetMeta = assetIndex.get(fileName);

        AttachmentManifestEntry entry = new AttachmentManifestEntry();
        entry.setPageId(pageId);
        entry.setFileName(fileName);
        entry.setRelativePath(relativePath);
        entry.setAttachmentId(assetMeta != null ? assetMeta.getAttachmentId() : null);
        entry.setMimeType(assetMeta != null ? assetMeta.getMediaType() : null);
        entry.setHeadingPath(headingPaths.getOrDefault(relativePath, headingPaths.get(fileName)));
        entry.setSchemaVersion(properties.schemaVersion());

        try {
            byte[] content = Files.readAllBytes(file);
            if (content.length > properties.maxFileSizeBytes()) {
                entry.setExtractionStatus(ExtractionStatus.SKIPPED);
                entry.setDetectedType(AttachmentType.UNKNOWN);
                entry.setDetectionMethod(DetectionMethod.UNKNOWN);
                entry.setErrorMessage("File exceeds max size limit");
                logStructured(entry, startNanos);
                return entry;
            }

            String checksum = checksumService.sha256(content);
            entry.setChecksum(checksum);
            log.debug(
                    "Attachment parse input pageId={} attachmentId={} fileName={} path={} checksum={} byteLength={}",
                    pageId,
                    entry.getAttachmentId(),
                    fileName,
                    file.toAbsolutePath().normalize(),
                    checksum,
                    content.length);

            AttachmentManifestEntry cached = previousByPath.get(relativePath);
            if (isCacheHit(cached, checksum)) {
                AttachmentManifestEntry cachedEntry = copyFromCache(entry, cached);
                logParsedAttachment(cachedEntry);
                return cachedEntry;
            }

            String declaredMime = entry.getMimeType();
            AttachmentDetectionResult detection =
                    inspector.inspectDeterministic(fileName, declaredMime, content);
            applyDetection(entry, detection);

            if (inspector.requiresVision(detection)
                    && properties.visionEnabled()
                    && inspector.isVisionEligible(fileName, content)) {
                runVisionAnalysis(entry, content, detection);
            } else if (entry.getExtractionStatus() == null) {
                entry.setExtractionStatus(
                        detection.searchableSummary() != null && !detection.searchableSummary().isBlank()
                                ? ExtractionStatus.SUCCESS
                                : ExtractionStatus.PARTIAL);
            }

            entry.setAnalyzedAt(Instant.now());
            logParsedAttachment(entry);
            logStructured(entry, startNanos);
            return entry;
        } catch (Exception ex) {
            entry.setExtractionStatus(ExtractionStatus.FAILED);
            entry.setDetectedType(entry.getDetectedType() != null ? entry.getDetectedType() : AttachmentType.UNKNOWN);
            entry.setDetectionMethod(
                    entry.getDetectionMethod() != null ? entry.getDetectionMethod() : DetectionMethod.UNKNOWN);
            entry.setErrorMessage(truncate(ex.getMessage(), 500));
            entry.setAnalyzedAt(Instant.now());
            logStructured(entry, startNanos);
            log.warn(
                    "Attachment analysis failed parentPageId={} pageId={} fileName={}: {}",
                    parentPageId,
                    pageId,
                    fileName,
                    ex.getMessage());
            return entry;
        }
    }

    private void runVisionAnalysis(
            AttachmentManifestEntry entry, byte[] content, AttachmentDetectionResult deterministic) {
        try {
            VisionAnalysisResult vision = visionService.analyzeImage(content, entry.getFileName());
            AttachmentType visionType = resolveVisionType(vision);
            entry.setDetectedType(visionType);
            entry.setDetectionMethod(DetectionMethod.VISION_MODEL);
            entry.setVisionModel(properties.visionModel());
            entry.setConfidence(vision.getConfidence());
            entry.setSearchableSummary(firstNonBlank(vision.getSearchableSummary(), vision.getPurpose(), vision.getTitle()));
            entry.setExtractedMetadata(buildVisionMetadata(vision));
            entry.setExtractionStatus(vision.getSearchableSummary() != null ? ExtractionStatus.SUCCESS : ExtractionStatus.PARTIAL);
        } catch (Exception ex) {
            if (deterministic != null && deterministic.type() != AttachmentType.UNKNOWN) {
                applyDetection(entry, deterministic);
                entry.setExtractionStatus(ExtractionStatus.PARTIAL);
            } else {
                entry.setDetectedType(AttachmentType.GENERIC_IMAGE);
                entry.setDetectionMethod(DetectionMethod.MIME_TYPE);
                entry.setExtractionStatus(ExtractionStatus.FAILED);
            }
            entry.setErrorMessage(truncate("Vision failed: " + ex.getMessage(), 500));
        }
    }

    private static AttachmentType resolveVisionType(VisionAnalysisResult vision) {
        if (vision.getClassification() != null && !vision.getClassification().isBlank()) {
            return AttachmentType.fromVisionLabel(vision.getClassification());
        }
        return AttachmentType.GENERIC_IMAGE;
    }

    private static Map<String, Object> buildVisionMetadata(VisionAnalysisResult vision) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "title", vision.getTitle());
        putIfPresent(metadata, "purpose", vision.getPurpose());
        putIfPresent(metadata, "visibleText", vision.getVisibleText());
        putIfPresent(metadata, "components", vision.getComponents());
        putIfPresent(metadata, "relationships", vision.getRelationships());
        putIfPresent(metadata, "userActions", vision.getUserActions());
        putIfPresent(metadata, "fields", vision.getFields());
        putIfPresent(metadata, "warnings", vision.getWarnings());
        return metadata.isEmpty() ? null : metadata;
    }

    private static void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        metadata.put(key, value);
    }

    private static void applyDetection(AttachmentManifestEntry entry, AttachmentDetectionResult detection) {
        entry.setDetectedType(detection.type());
        entry.setDetectionMethod(detection.method());
        entry.setSearchableSummary(detection.searchableSummary());
        entry.setConfidence(detection.confidence());
        entry.setExtractedMetadata(detection.extractedMetadata());
    }

    private boolean isCacheHit(AttachmentManifestEntry cached, String checksum) {
        return cached != null
                && checksum.equals(cached.getChecksum())
                && properties.schemaVersion() == cached.getSchemaVersion()
                && (cached.getVisionModel() == null
                        || properties.visionModel().equals(cached.getVisionModel()));
    }

    private AttachmentManifestEntry copyFromCache(AttachmentManifestEntry target, AttachmentManifestEntry cached) {
        target.setMimeType(cached.getMimeType());
        target.setChecksum(cached.getChecksum());
        target.setDetectedType(cached.getDetectedType());
        target.setDetectionMethod(DetectionMethod.CACHED);
        target.setExtractionStatus(cached.getExtractionStatus());
        target.setSearchableSummary(cached.getSearchableSummary());
        target.setConfidence(cached.getConfidence());
        target.setVisionModel(cached.getVisionModel());
        target.setExtractedMetadata(cached.getExtractedMetadata());
        target.setAnalyzedAt(cached.getAnalyzedAt());
        target.setSchemaVersion(cached.getSchemaVersion());
        target.setErrorMessage(cached.getErrorMessage());
        return target;
    }

    private Map<String, AttachmentManifestEntry> loadPreviousEntries(String parentPageId, String pageId) {
        try {
            return manifestStorageService.readManifestIndex(parentPageId, pageId);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Stream<Path> listAnalyzableFiles(String parentPageId, String pageId) throws IOException {
        Path assetsDir = fileStorageService.pageAssetsDirectory(parentPageId, pageId);
        if (!Files.isDirectory(assetsDir)) {
            return Stream.empty();
        }
        return Files.walk(assetsDir)
                .filter(Files::isRegularFile)
                .filter(path -> !path.toString().contains("/tables/") && !path.toString().contains("\\tables\\"))
                .filter(path -> !path.getFileName().toString().endsWith(".json"));
    }

    private String toRelativeAssetPath(String parentPageId, String pageId, Path file) {
        Path assetsDir = fileStorageService.pageAssetsDirectory(parentPageId, pageId);
        String relative = assetsDir.relativize(file).toString().replace('\\', '/');
        return "assets/" + relative;
    }

    private static Map<String, PageAssetDocument> indexAssets(List<PageAssetDocument> assets) {
        Map<String, PageAssetDocument> index = new HashMap<>();
        if (assets == null) {
            return index;
        }
        for (PageAssetDocument asset : assets) {
            if (asset.getFileName() != null) {
                index.put(asset.getFileName(), asset);
            }
        }
        return index;
    }

    private void logStructured(AttachmentManifestEntry entry, long startNanos) {
        long processingTimeMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info(
                "Attachment inspected pageId={} attachmentId={} fileName={} detectedType={} detectionMethod={} extractionStatus={} processingTimeMs={}",
                entry.getPageId(),
                entry.getAttachmentId(),
                entry.getFileName(),
                entry.getDetectedType(),
                entry.getDetectionMethod(),
                entry.getExtractionStatus(),
                processingTimeMs);
    }

    private void logParsedAttachment(AttachmentManifestEntry entry) {
        Map<String, Object> metadata = entry.getExtractedMetadata();
        List<?> labels = metadata != null && metadata.get("labels") instanceof List<?> list ? list : List.of();
        log.debug(
                "Attachment parse result attachmentId={} labelCount={} firstLabels={} detectedDiagramType={}",
                entry.getAttachmentId(),
                labels.size(),
                labels.stream().limit(10).toList(),
                metadata != null ? metadata.getOrDefault("diagramType", entry.getDetectedType()) : entry.getDetectedType());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
