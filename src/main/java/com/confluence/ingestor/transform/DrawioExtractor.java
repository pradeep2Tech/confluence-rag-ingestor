package com.confluence.ingestor.transform;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extracts text labels from draw.io {@code mxfile} / {@code mxGraphModel} XML.
 */
@Component
public class DrawioExtractor {

    private static final Pattern DRAWIO_MARKER =
            Pattern.compile("<mx(?:file|GraphModel)\\b", Pattern.CASE_INSENSITIVE);

    public boolean looksLikeDrawioXml(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return DRAWIO_MARKER.matcher(content).find();
    }

    public List<String> extractLabels(String drawioXml) {
        if (!looksLikeDrawioXml(drawioXml)) {
            return List.of();
        }

        Set<String> labels = new LinkedHashSet<>();
        Document document = Jsoup.parse(drawioXml, "", Parser.xmlParser());
        for (Element cell : document.select("mxCell[value]")) {
            String value = cell.attr("value");
            if (value.isBlank()) {
                continue;
            }
            String text = sanitizeLabel(value);
            if (!text.isBlank()) {
                labels.add(text);
            }
        }
        return new ArrayList<>(labels);
    }

    public String placeholder(ExtractedDiagram diagram) {
        return "[DIAGRAM:%s](%s%s)"
                .formatted(diagram.diagramId(), StorageDrawioReferenceExtractor.DIAGRAMS_ASSET_PREFIX, diagram.jsonFileName());
    }

    private static String sanitizeLabel(String rawValue) {
        String decoded = rawValue
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        if (decoded.contains("<")) {
            return Jsoup.parse(decoded).text().replace('\n', ' ').strip();
        }
        return decoded.replace('\n', ' ').strip();
    }
}
