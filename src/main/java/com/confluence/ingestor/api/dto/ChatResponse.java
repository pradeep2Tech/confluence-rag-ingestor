package com.confluence.ingestor.api.dto;

import java.util.List;

/**
 * Response for {@code POST /api/chat}.
 */
public record ChatResponse(
        ChatStatus status,
        String question,
        String parentPageId,
        String answer,
        List<ChatSource> sources,
        String message,
        String errorDetail
) {
    public static ChatResponse success(
            String question, String parentPageId, String answer, List<ChatSource> sources) {
        return new ChatResponse(
                ChatStatus.SUCCESS,
                question,
                parentPageId,
                answer,
                sources,
                "Answer generated.",
                null);
    }

    public static ChatResponse error(String question, String parentPageId, String message, String errorDetail) {
        return new ChatResponse(
                ChatStatus.ERROR,
                question,
                parentPageId,
                null,
                List.of(),
                message,
                errorDetail);
    }
}
