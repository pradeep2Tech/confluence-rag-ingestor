package com.confluence.ingestor.transform;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Structured table extracted from Confluence storage HTML for complex layouts
 * that cannot be represented faithfully as Markdown pipe tables.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtractedTable(
        String tableId,
        String fileName,
        boolean complex,
        int rowCount,
        int columnCount,
        String caption,
        List<TableRow> rows) {

    public record TableRow(List<TableCell> cells) {
    }

    public record TableCell(String text, int colspan, int rowspan, boolean header) {
    }
}
