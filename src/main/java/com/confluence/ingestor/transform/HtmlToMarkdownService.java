package com.confluence.ingestor.transform;

import com.confluence.ingestor.service.AttachmentDownloadService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Converts Confluence {@code body.storage} XHTML to Markdown.
 * <p>
 * Phase 3: headings, paragraphs, lists, links, emphasis, tables, code macros.
 * Phase 4: local {@code assets/} image links for Confluence attachments.
 * Phase 5: complex tables extracted to {@code assets/tables/} JSON with placeholders.
 * Phase 6: draw.io macros extracted to {@code assets/diagrams/} with label text.
 */
@Service
public class HtmlToMarkdownService {

    private static final Logger log = LoggerFactory.getLogger(HtmlToMarkdownService.class);

    private static final Pattern HEADING = Pattern.compile("h[1-6]", Pattern.CASE_INSENSITIVE);

    private final TableExtractor tableExtractor;

    public HtmlToMarkdownService(TableExtractor tableExtractor) {
        this.tableExtractor = tableExtractor;
    }

    @Observed(name = "transform.html.to.markdown")
    public MarkdownConversionResult toMarkdownWithTables(String storageHtml, String pageTitle) {
        TablePreprocessResult preprocessed = preprocessComplexTables(storageHtml);
        String markdown = toMarkdown(preprocessed.processedHtml(), pageTitle);
        int tableCount = preprocessed.extractedTables() != null ? preprocessed.extractedTables().size() : 0;
        log.debug(
                "Markdown conversion pageTitle={} markdownLength={} tablesExtracted={}",
                pageTitle,
                markdown != null ? markdown.length() : 0,
                tableCount);
        return new MarkdownConversionResult(markdown, preprocessed.extractedTables());
    }

    public String toMarkdown(String storageHtml, String pageTitle) {
        String title = pageTitle != null ? pageTitle.strip() : "";
        if (storageHtml == null || storageHtml.isBlank()) {
            return title.isEmpty() ? "" : "# " + title;
        }

        Document document = Jsoup.parse(wrapStorageHtml(storageHtml), "", Parser.xmlParser());
        List<String> parts = new ArrayList<>();
        for (Element child : topLevelElements(document)) {
            String block = convertBlock(child);
            if (!block.isBlank()) {
                parts.add(block);
            }
        }

        String body = String.join("\n\n", parts).strip();
        if (title.isEmpty()) {
            return body;
        }
        if (body.isEmpty()) {
            return "# " + title;
        }
        return ("# " + title + "\n\n" + body).strip();
    }

    TablePreprocessResult preprocessComplexTables(String storageHtml) {
        if (storageHtml == null || storageHtml.isBlank()) {
            return new TablePreprocessResult(storageHtml, List.of());
        }

        Document document = Jsoup.parse(wrapStorageHtml(storageHtml), "", Parser.xmlParser());
        Element root = document.selectFirst("root");
        if (root == null) {
            return new TablePreprocessResult(storageHtml, List.of());
        }

        List<ExtractedTable> extractedTables = new ArrayList<>();
        int tableIndex = 1;
        for (Element table : new ArrayList<>(root.select("table"))) {
            if (!tableExtractor.isComplex(table)) {
                continue;
            }
            String tableId = "table-" + tableIndex++;
            ExtractedTable extracted = tableExtractor.extract(table, tableId);
            extractedTables.add(extracted);
            Element placeholder = new Element("p");
            placeholder.text(tableExtractor.placeholder(extracted));
            table.replaceWith(placeholder);
        }

        String processedHtml = root.html();
        return new TablePreprocessResult(processedHtml, extractedTables);
    }

    private static String wrapStorageHtml(String storageHtml) {
        String trimmed = storageHtml.strip();
        if (trimmed.startsWith("<")) {
            return "<root>" + trimmed + "</root>";
        }
        return "<root><p>" + trimmed + "</p></root>";
    }

    private static List<Element> topLevelElements(Document document) {
        Element root = document.selectFirst("root");
        if (root == null) {
            return document.body() != null ? document.body().children() : List.of();
        }
        return root.children();
    }

    private String convertBlock(Element element) {
        String tag = normalizedTag(element);
        if (tag == null) {
            return inlineChildren(element).strip();
        }
        return switch (tag) {
            case "p" -> inlineChildren(element).strip();
            case "h1", "h2", "h3", "h4", "h5", "h6" -> heading(element, tag);
            case "ul" -> convertList(element, false);
            case "ol" -> convertList(element, true);
            case "table" -> tableExtractor.toMarkdownTable(element);
            case "pre" -> "```\n" + element.text().strip() + "\n```";
            case "blockquote" -> prefixLines(inlineChildren(element).strip(), "> ");
            case "hr" -> "---";
            case "ac:structured-macro" -> convertMacro(element);
            case "ac:image" -> convertImagePlaceholder(element);
            default -> {
                if (HEADING.matcher(tag).matches()) {
                    yield heading(element, tag);
                }
                String inline = inlineChildren(element).strip();
                yield inline.isEmpty() ? "" : inline;
            }
        };
    }

    private String convertMacro(Element element) {
        String macroName = element.attr("ac:name");
        if ("code".equalsIgnoreCase(macroName)) {
            Element body = element.selectFirst("ac|plain-text-body");
            if (body == null) {
                body = element.selectFirst("plain-text-body");
            }
            String code = body != null ? body.text().strip() : element.text().strip();
            return "```\n" + code + "\n```";
        }
        return inlineChildren(element).strip();
    }

    private String convertImagePlaceholder(Element element) {
        Element attachment = element.selectFirst("ri|attachment");
        if (attachment == null) {
            attachment = element.selectFirst("attachment");
        }
        String filename = attachment != null ? attachment.attr("ri:filename") : "";
        if (filename.isBlank() && attachment != null) {
            filename = attachment.attr("filename");
        }
        if (filename.isBlank()) {
            filename = "image";
        }
        String safeName = AttachmentDownloadService.sanitizeFileName(filename);
        return "![%s](assets/%s)".formatted(safeName, safeName);
    }

    private String heading(Element element, String tag) {
        int level = Integer.parseInt(tag.substring(1));
        String hashes = "#".repeat(Math.min(level, 6));
        return hashes + " " + inlineChildren(element).strip();
    }

    private String convertList(Element element, boolean ordered) {
        StringBuilder builder = new StringBuilder();
        Elements items = element.children().isEmpty() ? element.select("> li") : element.select("> li");
        int index = 1;
        for (Element item : items) {
            if (ordered) {
                builder.append(index++).append(". ");
            } else {
                builder.append("- ");
            }
            builder.append(inlineChildren(item).strip()).append('\n');
        }
        return builder.toString().strip();
    }

    private String inlineChildren(Element element) {
        StringBuilder builder = new StringBuilder();
        for (Node node : element.childNodes()) {
            builder.append(convertInline(node));
        }
        return builder.toString();
    }

    private String convertInline(Node node) {
        if (node instanceof TextNode textNode) {
            return textNode.getWholeText();
        }
        if (!(node instanceof Element element)) {
            return "";
        }
        String tag = normalizedTag(element);
        if (tag == null) {
            return inlineChildren(element);
        }
        return switch (tag) {
            case "br" -> "\n";
            case "strong", "b" -> "**" + inlineChildren(element).strip() + "**";
            case "em", "i" -> "*" + inlineChildren(element).strip() + "*";
            case "code" -> "`" + element.text().strip() + "`";
            case "a" -> {
                String href = element.attr("href");
                String text = inlineChildren(element).strip();
                if (href.isBlank()) {
                    yield text;
                }
                yield "[" + (text.isEmpty() ? href : text) + "](" + href + ")";
            }
            case "ac:link" -> {
                Element page = element.selectFirst("ri|page");
                if (page == null) {
                    page = element.selectFirst("page");
                }
                String linkText = inlineChildren(element).strip();
                if (page != null && linkText.isEmpty()) {
                    linkText = page.attr("ri:content-title");
                }
                yield linkText;
            }
            case "ac:image" -> convertImagePlaceholder(element);
            default -> inlineChildren(element);
        };
    }

    private static String prefixLines(String text, String prefix) {
        return text.lines().map(line -> prefix + line).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private static String normalizedTag(Element element) {
        String tag = element.tagName();
        if (tag == null) {
            return null;
        }
        return tag.toLowerCase();
    }

    public record MarkdownConversionResult(String markdown, List<ExtractedTable> extractedTables) {
    }

    record TablePreprocessResult(String processedHtml, List<ExtractedTable> extractedTables) {
    }
}
