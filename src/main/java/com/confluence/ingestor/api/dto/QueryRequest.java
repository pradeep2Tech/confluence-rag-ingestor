package com.confluence.ingestor.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/confluence/query}.
 */
public record QueryRequest(
        @NotBlank(message = "query is required")
        @Size(max = 4000, message = "query must be at most 4000 characters")
        String query,

        @Size(max = 64, message = "parentPageId must be at most 64 characters")
        String parentPageId,

        @Min(1) @Max(50)
        Integer topK,

        @Min(0) @Max(1)
        Double similarityThreshold
) {
    public QueryRequest {
        if (query != null) {
            query = query.strip();
        }
        if (parentPageId != null) {
            parentPageId = parentPageId.strip();
        }
    }

    public int resolvedTopK(int defaultTopK) {
        return topK != null ? topK : defaultTopK;
    }

    public double resolvedSimilarityThreshold(double defaultThreshold) {
        return similarityThreshold != null ? similarityThreshold : defaultThreshold;
    }

    public String normalizedParentPageId() {
        return parentPageId != null && !parentPageId.isBlank() ? parentPageId : null;
    }
}
