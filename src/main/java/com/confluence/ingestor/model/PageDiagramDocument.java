package com.confluence.ingestor.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One extracted draw.io diagram recorded in {@link PageDocument#getDiagrams()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageDiagramDocument {

    private String diagramId;
    private String diagramName;
    private String drawioFileName;
    private String jsonFileName;
    private String drawioLocalPath;
    private String jsonLocalPath;
    private int labelCount;

    public static PageDiagramDocument of(
            String diagramId,
            String diagramName,
            String drawioFileName,
            String jsonFileName,
            String drawioLocalPath,
            String jsonLocalPath,
            int labelCount) {
        PageDiagramDocument document = new PageDiagramDocument();
        document.setDiagramId(diagramId);
        document.setDiagramName(diagramName);
        document.setDrawioFileName(drawioFileName);
        document.setJsonFileName(jsonFileName);
        document.setDrawioLocalPath(drawioLocalPath);
        document.setJsonLocalPath(jsonLocalPath);
        document.setLabelCount(labelCount);
        return document;
    }

    public String getDiagramId() {
        return diagramId;
    }

    public void setDiagramId(String diagramId) {
        this.diagramId = diagramId;
    }

    public String getDiagramName() {
        return diagramName;
    }

    public void setDiagramName(String diagramName) {
        this.diagramName = diagramName;
    }

    public String getDrawioFileName() {
        return drawioFileName;
    }

    public void setDrawioFileName(String drawioFileName) {
        this.drawioFileName = drawioFileName;
    }

    public String getJsonFileName() {
        return jsonFileName;
    }

    public void setJsonFileName(String jsonFileName) {
        this.jsonFileName = jsonFileName;
    }

    public String getDrawioLocalPath() {
        return drawioLocalPath;
    }

    public void setDrawioLocalPath(String drawioLocalPath) {
        this.drawioLocalPath = drawioLocalPath;
    }

    public String getJsonLocalPath() {
        return jsonLocalPath;
    }

    public void setJsonLocalPath(String jsonLocalPath) {
        this.jsonLocalPath = jsonLocalPath;
    }

    public int getLabelCount() {
        return labelCount;
    }

    public void setLabelCount(int labelCount) {
        this.labelCount = labelCount;
    }
}
