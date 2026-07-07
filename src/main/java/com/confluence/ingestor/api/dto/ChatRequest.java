package com.confluence.ingestor.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/chat}.
 */
public record ChatRequest(
        @NotBlank(message = "question is required")
        @Size(max = 4000, message = "question must be at most 4000 characters")
        String question,

        @Size(max = 64, message = "parentPageId must be at most 64 characters")
        String parentPageId,

        @Min(1) @Max(20)
        Integer topK,

        @Min(0) @Max(1)
        Double similarityThreshold
) {
    public ChatRequest {
        if (question != null) {
            question = question.strip();
        }
        if (parentPageId != null) {
            parentPageId = parentPageId.strip();
        }
    }

    public String normalizedParentPageId() {
        return parentPageId != null && !parentPageId.isBlank() ? parentPageId : null;
    }
}
