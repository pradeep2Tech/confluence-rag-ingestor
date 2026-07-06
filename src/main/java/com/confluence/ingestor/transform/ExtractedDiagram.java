package com.confluence.ingestor.transform;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Structured draw.io diagram extracted from Confluence storage HTML.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtractedDiagram(
        String diagramId,
        String diagramName,
        String drawioFileName,
        String jsonFileName,
        List<String> labels) {
}
