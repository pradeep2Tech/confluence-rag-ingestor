package com.confluence.ingestor.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One extracted table artifact recorded in {@link PageDocument#getTables()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageTableDocument {

    private String tableId;
    private String fileName;
    private String localPath;
    private int rowCount;
    private int columnCount;
    private boolean complex;

    public static PageTableDocument of(
            String tableId,
            String fileName,
            String localPath,
            int rowCount,
            int columnCount,
            boolean complex) {
        PageTableDocument document = new PageTableDocument();
        document.setTableId(tableId);
        document.setFileName(fileName);
        document.setLocalPath(localPath);
        document.setRowCount(rowCount);
        document.setColumnCount(columnCount);
        document.setComplex(complex);
        return document;
    }

    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public int getColumnCount() {
        return columnCount;
    }

    public void setColumnCount(int columnCount) {
        this.columnCount = columnCount;
    }

    public boolean isComplex() {
        return complex;
    }

    public void setComplex(boolean complex) {
        this.complex = complex;
    }
}
