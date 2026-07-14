package com.confluence.ingestor.api.dto;

/**
 * Health of a single dependency (application, vector store, or model).
 */
public record ComponentHealthDto(String status, String message) {

    public static ComponentHealthDto up(String message) {
        return new ComponentHealthDto("UP", message);
    }

    public static ComponentHealthDto down(String message) {
        return new ComponentHealthDto("DOWN", message);
    }
}
