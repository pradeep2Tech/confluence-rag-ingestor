package com.confluence.ingestor.api.dto;

/**
 * Response for {@code POST /api/confluence/test}.
 */
public record ConfluenceTestResponse(
        boolean connected,
        String message,
        String displayName,
        String parentPageId,
        String pageTitle,
        String spaceKey
) {
    public static ConfluenceTestResponse success(
            String displayName, String parentPageId, String pageTitle, String spaceKey, String message) {
        return new ConfluenceTestResponse(true, message, displayName, parentPageId, pageTitle, spaceKey);
    }

    public static ConfluenceTestResponse failure(String message) {
        return new ConfluenceTestResponse(false, message, null, null, null, null);
    }
}
