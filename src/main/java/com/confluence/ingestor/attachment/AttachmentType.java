package com.confluence.ingestor.attachment;

/**
 * Classified attachment type after deterministic inspection and optional vision analysis.
 */
public enum AttachmentType {
    DRAWIO_SOURCE,
    DRAWIO_DIAGRAM,
    STATE_DIAGRAM,
    CLASS_DIAGRAM,
    ER_DIAGRAM,
    GENERIC_DIAGRAM,
    VISIO_SOURCE,
    VISIO_DIAGRAM,
    SVG_DIAGRAM,
    UI_SCREENSHOT,
    ARCHITECTURE_DIAGRAM,
    FLOWCHART,
    SEQUENCE_DIAGRAM,
    TABLE_IMAGE,
    PHOTO,
    PDF_DOCUMENT,
    PRESENTATION,
    GENERIC_IMAGE,
    OTHER,
    UNKNOWN;

    public static AttachmentType fromVisionLabel(String label) {
        if (label == null || label.isBlank()) {
            return UNKNOWN;
        }
        return switch (label.trim().toLowerCase()) {
            case "ui_screenshot" -> UI_SCREENSHOT;
            case "architecture_diagram" -> ARCHITECTURE_DIAGRAM;
            case "state_diagram" -> STATE_DIAGRAM;
            case "class_diagram" -> CLASS_DIAGRAM;
            case "er_diagram" -> ER_DIAGRAM;
            case "generic_diagram" -> GENERIC_DIAGRAM;
            case "flowchart" -> FLOWCHART;
            case "sequence_diagram" -> SEQUENCE_DIAGRAM;
            case "table" -> TABLE_IMAGE;
            case "photo" -> PHOTO;
            case "generic_image" -> GENERIC_IMAGE;
            case "other" -> OTHER;
            default -> UNKNOWN;
        };
    }
}
