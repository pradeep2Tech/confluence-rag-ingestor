package com.confluence.ingestor.transform;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts image attachment filenames referenced in Confluence {@code body.storage} XHTML.
 */
public final class StorageImageReferenceExtractor {

    private static final Pattern ATTACHMENT_PATH_FILENAME =
            Pattern.compile("/attachments/[^/]+/[^/]+/([^/?#]+)", Pattern.CASE_INSENSITIVE);

    private StorageImageReferenceExtractor() {
    }

    public static Set<String> extractReferencedFilenames(String storageHtml) {
        Set<String> filenames = new LinkedHashSet<>();
        if (storageHtml == null || storageHtml.isBlank()) {
            return filenames;
        }

        Document document = Jsoup.parse(wrapStorageHtml(storageHtml), "", Parser.xmlParser());
        for (Element imageMacro : document.select("ac|image, image")) {
            addAttachmentFilename(imageMacro, filenames);
        }
        for (Element img : document.select("img")) {
            addFilenameFromSrc(img.attr("src"), filenames);
        }
        return filenames;
    }

    private static void addAttachmentFilename(Element imageMacro, Set<String> filenames) {
        Element attachment = imageMacro.selectFirst("ri|attachment");
        if (attachment == null) {
            attachment = imageMacro.selectFirst("attachment");
        }
        if (attachment == null) {
            return;
        }
        String filename = attachment.attr("ri:filename");
        if (filename.isBlank()) {
            filename = attachment.attr("filename");
        }
        if (!filename.isBlank()) {
            filenames.add(filename);
        }
    }

    private static void addFilenameFromSrc(String src, Set<String> filenames) {
        if (src == null || src.isBlank()) {
            return;
        }
        Matcher matcher = ATTACHMENT_PATH_FILENAME.matcher(src);
        if (matcher.find()) {
            filenames.add(decodeUrl(matcher.group(1)));
            return;
        }
        int slash = src.lastIndexOf('/');
        if (slash >= 0 && slash < src.length() - 1) {
            filenames.add(decodeUrl(src.substring(slash + 1)));
        }
    }

    private static String decodeUrl(String value) {
        return value.replace("%20", " ").replace("%2F", "/");
    }

    private static String wrapStorageHtml(String storageHtml) {
        String trimmed = storageHtml.strip();
        if (trimmed.startsWith("<")) {
            return "<root>" + trimmed + "</root>";
        }
        return "<root><p>" + trimmed + "</p></root>";
    }
}
