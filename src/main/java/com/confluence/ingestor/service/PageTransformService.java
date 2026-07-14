package com.confluence.ingestor.service;

import com.confluence.ingestor.confluence.AttachmentClient;
import com.confluence.ingestor.confluence.ConfluenceClient;
import com.confluence.ingestor.confluence.ConfluenceClientError;
import com.confluence.ingestor.confluence.dto.ConfluencePageContentDto;
import com.confluence.ingestor.model.AttachmentsManifestDocument;
import com.confluence.ingestor.model.PageAssetDocument;
import com.confluence.ingestor.model.PageDiagramDocument;
import com.confluence.ingestor.model.PageManifestEntry;
import com.confluence.ingestor.model.PageTableDocument;
import com.confluence.ingestor.storage.ManifestService;
import com.confluence.ingestor.storage.AttachmentManifestStorageService;
import com.confluence.ingestor.storage.PageIngestionStateService;
import com.confluence.ingestor.storage.PageStorageService;
import com.confluence.ingestor.storage.PageStorageService.PageArtifacts;
import com.confluence.ingestor.service.DrawioArtifactService.DrawioProcessResult;
import com.confluence.ingestor.transform.HtmlToMarkdownService;
import com.confluence.ingestor.transform.HtmlToMarkdownService.MarkdownConversionResult;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Transforms a single Confluence page to on-disk Markdown + metadata + image assets.
 */
@Service
@Observed(name = "page.transform")
public class PageTransformService {

    private static final Logger log = LoggerFactory.getLogger(PageTransformService.class);

    private final HtmlToMarkdownService htmlToMarkdownService;
    private final AttachmentDownloadService attachmentDownloadService;
    private final DrawioArtifactService drawioArtifactService;
    private final TableArtifactService tableArtifactService;
    private final AttachmentAnalysisService attachmentAnalysisService;
    private final MarkdownAttachmentEnrichmentService markdownAttachmentEnrichmentService;
    private final AttachmentManifestStorageService attachmentManifestStorageService;
    private final PageStorageService pageStorageService;
    private final ManifestService manifestService;
    private final PageIngestionStateService pageIngestionStateService;

    public PageTransformService(
            HtmlToMarkdownService htmlToMarkdownService,
            AttachmentDownloadService attachmentDownloadService,
            DrawioArtifactService drawioArtifactService,
            TableArtifactService tableArtifactService,
            AttachmentAnalysisService attachmentAnalysisService,
            MarkdownAttachmentEnrichmentService markdownAttachmentEnrichmentService,
            AttachmentManifestStorageService attachmentManifestStorageService,
            PageStorageService pageStorageService,
            ManifestService manifestService,
            PageIngestionStateService pageIngestionStateService) {
        this.htmlToMarkdownService = htmlToMarkdownService;
        this.attachmentDownloadService = attachmentDownloadService;
        this.drawioArtifactService = drawioArtifactService;
        this.tableArtifactService = tableArtifactService;
        this.attachmentAnalysisService = attachmentAnalysisService;
        this.markdownAttachmentEnrichmentService = markdownAttachmentEnrichmentService;
        this.attachmentManifestStorageService = attachmentManifestStorageService;
        this.pageStorageService = pageStorageService;
        this.manifestService = manifestService;
        this.pageIngestionStateService = pageIngestionStateService;
    }

    public TransformResult transformPage(
            String parentPageId, PageManifestEntry entry, ConfluenceClient client) {
        String pageId = entry.getPageId();
        try {
            ConfluencePageContentDto page = client.getPageContent(pageId);
            String title = page.getTitle() != null ? page.getTitle() : entry.getTitle();
            String storageHtml = page.storageHtml();

            AttachmentClient attachmentClient = new AttachmentClient(client);
            DrawioProcessResult drawioResult =
                    drawioArtifactService.process(parentPageId, pageId, storageHtml, attachmentClient);
            String htmlForMarkdown = drawioResult.processedHtml();

            List<PageAssetDocument> assets = attachmentDownloadService.downloadReferencedImages(
                    parentPageId, pageId, htmlForMarkdown, attachmentClient);

            MarkdownConversionResult conversion =
                    htmlToMarkdownService.toMarkdownWithTables(htmlForMarkdown, title);
            List<PageTableDocument> tables = tableArtifactService.writeTableArtifacts(
                    parentPageId, pageId, conversion.extractedTables());
            List<PageDiagramDocument> diagrams = drawioResult.diagrams();

            String markdown = conversion.markdown();
            AttachmentsManifestDocument attachmentsManifest = null;
            try {
                attachmentsManifest =
                        attachmentAnalysisService.analyzePageAttachments(parentPageId, pageId, markdown, assets);
            } catch (Exception ex) {
                log.warn(
                        "Attachment analysis failed for parentPageId={} pageId={} - continuing transform: {}",
                        parentPageId,
                        pageId,
                        ex.getMessage());
            }
            if (attachmentsManifest == null) {
                attachmentsManifest = readExistingAttachmentManifest(parentPageId, pageId);
            }
            markdown = markdownAttachmentEnrichmentService.enrich(markdown, attachmentsManifest);

            String webUrl = client.buildWebUrl(page);
            PageArtifacts artifacts = pageStorageService.writePageArtifacts(
                    parentPageId, page, webUrl, markdown, assets, tables, diagrams);

            updatePageSuccess(parentPageId, pageId, title, webUrl, artifacts);

            log.info(
                    "Markdown extracted for parentPageId={} pageId={} assets={} tables={} diagrams={}",
                    parentPageId,
                    pageId,
                    assets.size(),
                    tables.size(),
                    diagrams.size());
            return TransformResult.success(pageId);
        } catch (ConfluenceClientError ex) {
            return fail(parentPageId, pageId, ex.getMessage());
        } catch (Exception ex) {
            return fail(parentPageId, pageId, ex.getMessage());
        }
    }

    private AttachmentsManifestDocument readExistingAttachmentManifest(String parentPageId, String pageId) {
        try {
            return attachmentManifestStorageService.readManifest(parentPageId, pageId);
        } catch (Exception ex) {
            return null;
        }
    }

    private void updatePageSuccess(
            String parentPageId,
            String pageId,
            String title,
            String webUrl,
            PageArtifacts artifacts) {
        try {
            if (pageIngestionStateService.isPerPageStateEnabled()) {
                pageIngestionStateService.recordPageState(parentPageId, pageId, "transform", state -> {
                    state.setMarkdownExtracted(true);
                    state.setMarkdownPath(artifacts.markdownPath());
                    state.setMetadataPath(artifacts.metadataPath());
                    state.setAssetsDirectory(artifacts.assetsDirectory());
                    state.setLastError(null);
                    state.setNoOfRetries(0);
                });
            } else {
                manifestService.mutateManifest(parentPageId, manifest -> {
                    PageManifestEntry row = manifestService.findPageEntry(manifest, pageId);
                    if (row != null) {
                        row.setTitle(title);
                        row.setWebUrl(webUrl);
                        row.setMarkdownExtracted(true);
                        row.setMarkdownPath(artifacts.markdownPath());
                        row.setMetadataPath(artifacts.metadataPath());
                        row.setAssetsDirectory(artifacts.assetsDirectory());
                        row.setLastError(null);
                        row.setNoOfRetries(0);
                    }
                });
            }
        } catch (Exception ex) {
            log.warn(
                    "Could not update page transform success for parentPageId={} pageId={}: {}",
                    parentPageId,
                    pageId,
                    ex.getMessage());
        }
    }

    private TransformResult fail(String parentPageId, String pageId, String message) {
        String error = message != null && message.length() > 2000 ? message.substring(0, 2000) : message;
        try {
            if (pageIngestionStateService.isPerPageStateEnabled()) {
                pageIngestionStateService.recordPageState(parentPageId, pageId, "transform", state -> {
                    state.setLastError(error);
                    state.setNoOfRetries(state.getNoOfRetries() + 1);
                });
            } else {
                manifestService.mutateManifest(parentPageId, manifest -> {
                    PageManifestEntry row = manifestService.findPageEntry(manifest, pageId);
                    if (row != null) {
                        row.setLastError(error);
                        row.setNoOfRetries(row.getNoOfRetries() + 1);
                    }
                });
            }
        } catch (Exception ex) {
            log.warn(
                    "Could not update manifest failure for parentPageId={} pageId={}: {}",
                    parentPageId,
                    pageId,
                    ex.getMessage());
        }
        log.warn("Markdown extraction failed for parentPageId={} pageId={}: {}", parentPageId, pageId, error);
        return TransformResult.failure(pageId, error);
    }

    public record TransformResult(String pageId, boolean success, String error) {
        public static TransformResult success(String pageId) {
            return new TransformResult(pageId, true, null);
        }

        public static TransformResult failure(String pageId, String error) {
            return new TransformResult(pageId, false, error);
        }
    }
}
