package com.confluence.ingestor.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/confluence/test}.
 */
public record ConfluenceTestRequest(
        @Size(max = 512)
        String baseUrl,

        @Size(max = 512)
        String confluenceTarget,

        Boolean verifySsl,

        String pat
) {
}
