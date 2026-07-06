package com.confluence.ingestor.service;

import com.confluence.ingestor.confluence.AttachmentClient;
import com.confluence.ingestor.confluence.ConfluenceClientError;
import com.confluence.ingestor.confluence.dto.ConfluenceAttachmentDto;
import com.confluence.ingestor.config.IngestorProperties;
import com.confluence.ingestor.model.PageAssetDocument;
import com.confluence.ingestor.storage.FileStorageService;
import com.confluence.ingestor.transform.StorageImageReferenceExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Downloads image attachments referenced in page storage HTML into the local {@code assets/} directory.
 */
@Service
public class AttachmentDownloadService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentDownloadService.class);

    private final IngestorProperties properties;
    private final FileStorageService fileStorageService;

    public AttachmentDownloadService(IngestorProperties properties, FileStorageService fileStorageService) {
        this.properties = properties;
        this.fileStorageService = fileStorageService;
    }

    public List<PageAssetDocument> downloadReferencedImages(
            String parentPageId,
            String pageId,
            String storageHtml,
            AttachmentClient attachmentClient) throws IOException {
        Set<String> referencedFilenames = StorageImageReferenceExtractor.extractReferencedFilenames(storageHtml);
        if (referencedFilenames.isEmpty()) {
            return List.of();
        }

        Map<String, ConfluenceAttachmentDto> attachmentsByTitle = indexAttachments(attachmentClient, pageId);
        Path assetsDirectory = fileStorageService.pageAssetsDirectory(parentPageId, pageId);
        List<PageAssetDocument> downloaded = new ArrayList<>();

        for (String filename : referencedFilenames) {
            ConfluenceAttachmentDto attachment = attachmentsByTitle.get(filename);
            if (attachment == null) {
                log.warn(
                        "Referenced attachment not found for parentPageId={} pageId={} filename={}",
                        parentPageId,
                        pageId,
                        filename);
                continue;
            }
            if (!isAllowedImage(filename)) {
                log.debug("Skipping non-image attachment filename={}", filename);
                continue;
            }

            String downloadPath = attachment.downloadPath();
            if (downloadPath == null || downloadPath.isBlank()) {
                log.warn("Attachment has no download URL for pageId={} filename={}", pageId, filename);
                continue;
            }

            try {
                byte[] content = attachmentClient.downloadAttachment(downloadPath);
                String safeName = sanitizeFileName(filename);
                Path target = assetsDirectory.resolve(safeName);
                fileStorageService.writeBytesAtomic(target, content);
                downloaded.add(PageAssetDocument.of(
                        safeName,
                        attachment.mediaType(),
                        content.length,
                        attachment.getId(),
                        fileStorageService.displayPath(target)));
                log.info(
                        "Downloaded attachment parentPageId={} pageId={} fileName={} ({} bytes)",
                        parentPageId,
                        pageId,
                        safeName,
                        content.length);
            } catch (ConfluenceClientError ex) {
                log.warn(
                        "Failed to download attachment parentPageId={} pageId={} filename={}: {}",
                        parentPageId,
                        pageId,
                        filename,
                        ex.getMessage());
            }
        }

        return downloaded;
    }

    private Map<String, ConfluenceAttachmentDto> indexAttachments(AttachmentClient attachmentClient, String pageId) {
        Map<String, ConfluenceAttachmentDto> byTitle = new HashMap<>();
        for (ConfluenceAttachmentDto attachment : attachmentClient.listAttachments(pageId)) {
            if (attachment.getTitle() != null && !attachment.getTitle().isBlank()) {
                byTitle.putIfAbsent(attachment.getTitle(), attachment);
            }
        }
        return byTitle;
    }

    private boolean isAllowedImage(String filename) {
        String extension = extensionOf(filename);
        if (extension.isBlank()) {
            return false;
        }
        return properties.allowedImageExtensions().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(allowed -> allowed.equals(extension));
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static String sanitizeFileName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "attachment";
        }
        return filename.replaceAll("[<>:\"/\\\\|?*\\u0000]", "_").strip();
    }
}
