package com.confluence.ingestor.attachment;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps asset relative paths to the Markdown heading path where they appear.
 */
@Component
public class MarkdownHeadingPathResolver {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern IMAGE_REF = Pattern.compile("!\\[[^\\]]*]\\(([^)]+)\\)");
    private static final String HEADING_SEPARATOR = " > ";

    public Map<String, String> resolveHeadingPaths(String markdown) {
        Map<String, String> pathToHeading = new LinkedHashMap<>();
        if (markdown == null || markdown.isBlank()) {
            return pathToHeading;
        }

        String[] headingTitles = new String[6];
        String currentHeadingPath = "";

        for (String line : markdown.split("\n", -1)) {
            Matcher headingMatcher = HEADING.matcher(line);
            if (headingMatcher.matches()) {
                int level = headingMatcher.group(1).length();
                String title = headingMatcher.group(2).strip();
                headingTitles[level - 1] = title;
                for (int i = level; i < headingTitles.length; i++) {
                    headingTitles[i] = null;
                }
                currentHeadingPath = buildHeadingPath(headingTitles);
                continue;
            }

            Matcher imageMatcher = IMAGE_REF.matcher(line);
            while (imageMatcher.find()) {
                String assetPath = normalizeAssetPath(imageMatcher.group(1));
                pathToHeading.putIfAbsent(assetPath, currentHeadingPath);
            }
        }
        return pathToHeading;
    }

    private static String normalizeAssetPath(String rawPath) {
        String path = rawPath.strip().replace('\\', '/');
        if (path.startsWith("./")) {
            path = path.substring(2);
        }
        return path;
    }

    private static String buildHeadingPath(String[] headingTitles) {
        List<String> parts = new java.util.ArrayList<>();
        for (String title : headingTitles) {
            if (title != null && !title.isBlank()) {
                parts.add(title);
            }
        }
        return String.join(HEADING_SEPARATOR, parts);
    }
}
