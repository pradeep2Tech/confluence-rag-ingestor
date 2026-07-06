package com.confluence.ingestor.service;

import com.confluence.ingestor.confluence.AttachmentClient;
import com.confluence.ingestor.confluence.ConfluenceClientError;
import com.confluence.ingestor.confluence.dto.ConfluenceAttachmentDto;
import com.confluence.ingestor.config.IngestorProperties;
import com.confluence.ingestor.model.PageDiagramDocument;
import com.confluence.ingestor.storage.FileStorageService;
import com.confluence.ingestor.transform.DrawioExtractor;
import com.confluence.ingestor.transform.DrawioReference;
import com.confluence.ingestor.transform.ExtractedDiagram;
import com.confluence.ingestor.transform.StorageDrawioReferenceExtractor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Extracts draw.io diagrams from page storage HTML, persists assets under {@code assets/diagrams/},
 * and replaces macros with Markdown-friendly placeholders plus label lists.
 */
@Service
public class DrawioArtifactService {

    private static final Logger log = LoggerFactory.getLogger(DrawioArtifactService.class);

    private final IngestorProperties properties;
    private final FileStorageService fileStorageService;
    private final DrawioExtractor drawioExtractor;

    public DrawioArtifactService(
            IngestorProperties properties,
            FileStorageService fileStorageService,
            DrawioExtractor drawioExtractor) {
        this.properties = properties;
        this.fileStorageService = fileStorageService;
        this.drawioExtractor = drawioExtractor;
    }

    public DrawioProcessResult process(
            String parentPageId,
            String pageId,
            String storageHtml,
            AttachmentClient attachmentClient) throws IOException {
        if (storageHtml == null || storageHtml.isBlank()) {
            return new DrawioProcessResult(storageHtml, List.of());
        }

        Document document = Jsoup.parse(wrapStorageHtml(storageHtml), "", Parser.xmlParser());
        Element root = document.selectFirst("root");
        if (root == null) {
            return new DrawioProcessResult(storageHtml, List.of());
        }

        List<DrawioReference> references = StorageDrawioReferenceExtractor.extractReferences(root);
        if (references.isEmpty()) {
            return new DrawioProcessResult(storageHtml, List.of());
        }

        Files.createDirectories(fileStorageService.pageDiagramsDirectory(parentPageId, pageId));

        List<PageDiagramDocument> diagrams = new ArrayList<>();
        int diagramIndex = 1;
        AttachmentIndex attachmentIndex = new AttachmentIndex(attachmentClient, pageId);

        for (DrawioReference reference : references) {
            String diagramId = "diagram-" + diagramIndex++;
            String drawioContent = resolveDrawioContent(reference, attachmentIndex, attachmentClient);
            if (drawioContent == null || drawioContent.isBlank()) {
                log.warn(
                        "Could not resolve draw.io content for parentPageId={} pageId={} diagramName={}",
                        parentPageId,
                        pageId,
                        reference.diagramName());
                replaceMacro(reference.macroElement(), "[DIAGRAM:" + diagramId + " (unavailable)]");
                continue;
            }

            List<String> labels = drawioExtractor.extractLabels(drawioContent);
            String drawioFileName = diagramId + ".drawio";
            String jsonFileName = diagramId + ".json";
            String diagramName = reference.diagramName().isBlank() ? diagramId : reference.diagramName();

            Path drawioPath = fileStorageService.pageDiagramPath(parentPageId, pageId, drawioFileName);
            Path jsonPath = fileStorageService.pageDiagramPath(parentPageId, pageId, jsonFileName);
            fileStorageService.writeTextAtomic(drawioPath, drawioContent);
            ExtractedDiagram extracted =
                    new ExtractedDiagram(diagramId, diagramName, drawioFileName, jsonFileName, labels);
            fileStorageService.writeJsonAtomic(jsonPath, extracted);

            diagrams.add(PageDiagramDocument.of(
                    diagramId,
                    diagramName,
                    drawioFileName,
                    jsonFileName,
                    fileStorageService.displayPath(drawioPath),
                    fileStorageService.displayPath(jsonPath),
                    labels.size()));

            replaceMacro(reference.macroElement(), drawioExtractor.placeholder(extracted), labels);
            log.info(
                    "Extracted draw.io diagram parentPageId={} pageId={} diagramId={} labels={}",
                    parentPageId,
                    pageId,
                    diagramId,
                    labels.size());
        }

        return new DrawioProcessResult(root.html(), diagrams);
    }

    private String resolveDrawioContent(
            DrawioReference reference,
            AttachmentIndex attachmentIndex,
            AttachmentClient attachmentClient) {
        if (drawioExtractor.looksLikeDrawioXml(reference.embeddedXml())) {
            return reference.embeddedXml();
        }

        if (!reference.attachmentFilename().isBlank()) {
            String content = downloadAttachmentContent(
                    attachmentIndex.byTitle(), attachmentClient, reference.attachmentFilename());
            if (content != null) {
                return content;
            }
        }

        if (!reference.diagramName().isBlank()) {
            for (String candidate : candidateFilenames(reference.diagramName())) {
                String content = downloadAttachmentContent(
                        attachmentIndex.byTitle(), attachmentClient, candidate);
                if (content != null) {
                    return content;
                }
            }
        }
        return null;
    }

    private String downloadAttachmentContent(
            Map<String, ConfluenceAttachmentDto> attachmentsByTitle,
            AttachmentClient attachmentClient,
            String filename) {
        ConfluenceAttachmentDto attachment = attachmentsByTitle.get(filename);
        if (attachment == null || !isAllowedDrawio(filename)) {
            return null;
        }
        String downloadPath = attachment.downloadPath();
        if (downloadPath == null || downloadPath.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = attachmentClient.downloadAttachment(downloadPath);
            String content = new String(bytes, StandardCharsets.UTF_8);
            return drawioExtractor.looksLikeDrawioXml(content) ? content : null;
        } catch (ConfluenceClientError ex) {
            log.warn("Failed to download draw.io attachment filename={}: {}", filename, ex.getMessage());
            return null;
        }
    }

    private List<String> candidateFilenames(String diagramName) {
        String safeName = AttachmentDownloadService.sanitizeFileName(diagramName);
        List<String> candidates = new ArrayList<>();
        for (String extension : properties.allowedDrawioExtensions()) {
            candidates.add(safeName + "." + extension.toLowerCase(Locale.ROOT));
        }
        return candidates;
    }

    private boolean isAllowedDrawio(String filename) {
        String extension = extensionOf(filename);
        if (extension.isBlank()) {
            return false;
        }
        return properties.allowedDrawioExtensions().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(allowed -> allowed.equals(extension));
    }

    private static Map<String, ConfluenceAttachmentDto> indexAttachments(
            AttachmentClient attachmentClient, String pageId) {
        Map<String, ConfluenceAttachmentDto> byTitle = new HashMap<>();
        for (ConfluenceAttachmentDto attachment : attachmentClient.listAttachments(pageId)) {
            if (attachment.getTitle() != null && !attachment.getTitle().isBlank()) {
                byTitle.putIfAbsent(attachment.getTitle(), attachment);
            }
        }
        return byTitle;
    }

    private static final class AttachmentIndex {
        private final AttachmentClient attachmentClient;
        private final String pageId;
        private Map<String, ConfluenceAttachmentDto> byTitle;

        private AttachmentIndex(AttachmentClient attachmentClient, String pageId) {
            this.attachmentClient = attachmentClient;
            this.pageId = pageId;
        }

        private Map<String, ConfluenceAttachmentDto> byTitle() {
            if (byTitle == null) {
                byTitle = indexAttachments(attachmentClient, pageId);
            }
            return byTitle;
        }
    }

    private static void replaceMacro(Element macro, String placeholderText) {
        replaceMacro(macro, placeholderText, List.of());
    }

    private static void replaceMacro(Element macro, String placeholderText, List<String> labels) {
        Element paragraph = new Element("p");
        paragraph.text(placeholderText);
        macro.replaceWith(paragraph);
        if (!labels.isEmpty()) {
            Element list = new Element("ul");
            for (String label : labels) {
                list.appendElement("li").text(label);
            }
            paragraph.after(list);
        }
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String wrapStorageHtml(String storageHtml) {
        String trimmed = storageHtml.strip();
        if (trimmed.startsWith("<")) {
            return "<root>" + trimmed + "</root>";
        }
        return "<root><p>" + trimmed + "</p></root>";
    }

    public record DrawioProcessResult(String processedHtml, List<PageDiagramDocument> diagrams) {
    }
}
