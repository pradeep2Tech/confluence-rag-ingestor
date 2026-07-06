package com.confluence.ingestor.transform;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects complex Confluence tables and extracts structured cell data.
 * <p>
 * Simple tables are converted to Markdown pipe syntax. Complex tables (merged cells,
 * nested tables, uneven column counts) are extracted to JSON with a Markdown placeholder.
 */
@Component
public class TableExtractor {

    public static final String TABLES_ASSET_PREFIX = "assets/tables/";

    public boolean isComplex(Element table) {
        if (table.select("table").size() > 1) {
            return true;
        }
        Elements rows = table.select("tr");
        if (rows.isEmpty()) {
            return false;
        }
        int expectedColumns = -1;
        for (Element row : rows) {
            int rowColumns = effectiveColumnCount(row);
            if (rowColumns == 0) {
                continue;
            }
            if (expectedColumns < 0) {
                expectedColumns = rowColumns;
            } else if (rowColumns != expectedColumns) {
                return true;
            }
            for (Element cell : row.select("th,td")) {
                if (spanValue(cell, "colspan") > 1 || spanValue(cell, "rowspan") > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    public ExtractedTable extract(Element table, String tableId) {
        String fileName = tableId + ".json";
        Elements rows = table.select("tr");
        List<ExtractedTable.TableRow> extractedRows = new ArrayList<>();
        int maxColumns = 0;
        for (Element row : rows) {
            List<ExtractedTable.TableCell> cells = new ArrayList<>();
            for (Element cell : row.select("th,td")) {
                cells.add(new ExtractedTable.TableCell(
                        sanitizeCellText(cell),
                        spanValue(cell, "colspan"),
                        spanValue(cell, "rowspan"),
                        "th".equalsIgnoreCase(cell.tagName())));
            }
            if (!cells.isEmpty()) {
                extractedRows.add(new ExtractedTable.TableRow(cells));
                maxColumns = Math.max(maxColumns, effectiveColumnCount(row));
            }
        }
        return new ExtractedTable(
                tableId,
                fileName,
                isComplex(table),
                extractedRows.size(),
                maxColumns,
                extractCaption(table),
                extractedRows);
    }

    public String toMarkdownTable(Element table) {
        Elements rows = table.select("tr");
        if (rows.isEmpty()) {
            return "";
        }
        List<String> markdownRows = new ArrayList<>();
        List<String> headerCells = cellTexts(rows.getFirst().select("th,td"));
        if (headerCells.isEmpty()) {
            return "";
        }
        markdownRows.add("| " + String.join(" | ", headerCells) + " |");
        markdownRows.add("| " + String.join(" | ", headerCells.stream().map(ignored -> "---").toList()) + " |");
        for (int i = 1; i < rows.size(); i++) {
            List<String> cells = cellTexts(rows.get(i).select("td,th"));
            if (!cells.isEmpty()) {
                markdownRows.add("| " + String.join(" | ", cells) + " |");
            }
        }
        return String.join("\n", markdownRows);
    }

    public String placeholder(ExtractedTable table) {
        return "[TABLE:%s](%s%s)".formatted(table.tableId(), TABLES_ASSET_PREFIX, table.fileName());
    }

    private static int effectiveColumnCount(Element row) {
        int count = 0;
        for (Element cell : row.select("th,td")) {
            count += Math.max(1, spanValue(cell, "colspan"));
        }
        return count;
    }

    private static int spanValue(Element cell, String attribute) {
        String raw = cell.attr(attribute);
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private static String extractCaption(Element table) {
        Element caption = table.selectFirst("caption");
        if (caption == null) {
            return null;
        }
        String text = caption.text().strip();
        return text.isEmpty() ? null : text;
    }

    private static List<String> cellTexts(Elements cells) {
        List<String> values = new ArrayList<>();
        for (Element cell : cells) {
            values.add(sanitizeCellText(cell));
        }
        return values;
    }

    private static String sanitizeCellText(Element cell) {
        return cell.text().replace("|", "\\|").replace('\n', ' ').strip();
    }
}
