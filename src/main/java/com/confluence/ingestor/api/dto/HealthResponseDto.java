package com.confluence.ingestor.api.dto;

/**
 * Response for {@code GET /api/health}.
 */
public record HealthResponseDto(
        ComponentHealthDto application,
        ComponentHealthDto vectorStore,
        ComponentHealthDto model
) {
}
