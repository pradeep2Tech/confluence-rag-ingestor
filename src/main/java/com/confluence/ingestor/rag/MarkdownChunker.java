package com.confluence.ingestor.rag;

import com.confluence.ingestor.model.ChunkDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits Markdown into heading-scoped chunks for RAG ingestion.
 */
@Component
public class MarkdownChunker {

    private static final Logger log = LoggerFactory.getLogger(MarkdownChunker.class);

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final String HEADING_SEPARATOR = " > ";

    public List<ChunkDocument> chunk(
            String markdown,
            String parentPageId,
            String pageId,
            Map<String, Object> metadata,
            int maxChunkCharacters) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }

        List<Section> sections = splitSections(markdown);
        List<ChunkDocument> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (Section section : sections) {
            String body = section.body().strip();
            if (body.isBlank()) {
                continue;
            }
            for (String part : splitOversizedBody(body, maxChunkCharacters)) {
                ChunkDocument chunk = new ChunkDocument();
                chunk.setChunkId(pageId + "-" + chunkIndex);
                chunk.setPageId(pageId);
                chunk.setParentPageId(parentPageId);
                chunk.setHeadingPath(section.headingPath());
                chunk.setChunkIndex(chunkIndex++);
                chunk.setText(part);
                chunk.setMetadata(metadata != null ? Map.copyOf(metadata) : null);
                chunks.add(chunk);
            }
        }
        log.debug(
                "Chunked markdown parentPageId={} pageId={} chunkCount={} maxChunkCharacters={}",
                parentPageId,
                pageId,
                chunks.size(),
                maxChunkCharacters);
        return chunks;
    }

    private static List<Section> splitSections(String markdown) {
        List<Section> sections = new ArrayList<>();
        String[] headingTitles = new String[6];
        StringBuilder buffer = new StringBuilder();
        String currentHeadingPath = "";

        for (String line : markdown.split("\n", -1)) {
            Matcher matcher = HEADING.matcher(line);
            if (matcher.matches()) {
                flushSection(sections, currentHeadingPath, buffer);
                int level = matcher.group(1).length();
                String title = matcher.group(2).strip();
                headingTitles[level - 1] = title;
                for (int i = level; i < headingTitles.length; i++) {
                    headingTitles[i] = null;
                }
                currentHeadingPath = buildHeadingPath(headingTitles);
                continue;
            }
            if (!buffer.isEmpty()) {
                buffer.append('\n');
            }
            buffer.append(line);
        }

        flushSection(sections, currentHeadingPath, buffer);
        return sections;
    }

    private static void flushSection(List<Section> sections, String headingPath, StringBuilder buffer) {
        if (buffer.isEmpty()) {
            return;
        }
        sections.add(new Section(headingPath, buffer.toString()));
        buffer.setLength(0);
    }

    private static String buildHeadingPath(String[] headingTitles) {
        List<String> parts = new ArrayList<>();
        for (String title : headingTitles) {
            if (title != null && !title.isBlank()) {
                parts.add(title);
            }
        }
        return String.join(HEADING_SEPARATOR, parts);
    }

    private static List<String> splitOversizedBody(String body, int maxChunkCharacters) {
        if (body.length() <= maxChunkCharacters) {
            return List.of(body);
        }

        List<String> parts = new ArrayList<>();
        String[] paragraphs = body.split("\n\n");
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            String candidate = paragraph.strip();
            if (candidate.isBlank()) {
                continue;
            }
            if (current.isEmpty()) {
                appendPart(parts, current, candidate, maxChunkCharacters);
                continue;
            }
            String combined = current + "\n\n" + candidate;
            if (combined.length() <= maxChunkCharacters) {
                current.setLength(0);
                current.append(combined);
            } else {
                parts.add(current.toString());
                current.setLength(0);
                appendPart(parts, current, candidate, maxChunkCharacters);
            }
        }

        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static void appendPart(
            List<String> parts, StringBuilder current, String candidate, int maxChunkCharacters) {
        if (candidate.length() <= maxChunkCharacters) {
            current.append(candidate);
            return;
        }
        int start = 0;
        while (start < candidate.length()) {
            int end = Math.min(candidate.length(), start + maxChunkCharacters);
            parts.add(candidate.substring(start, end).strip());
            start = end;
        }
    }

    public static Map<String, Object> pageMetadata(
            String title, String webUrl, String spaceKey, int version) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (title != null && !title.isBlank()) {
            metadata.put("title", title);
        }
        if (webUrl != null && !webUrl.isBlank()) {
            metadata.put("webUrl", webUrl);
        }
        if (spaceKey != null && !spaceKey.isBlank()) {
            metadata.put("spaceKey", spaceKey);
        }
        metadata.put("version", version);
        return metadata;
    }

    private record Section(String headingPath, String body) {
    }
}
