package com.confluence.ingestor.api.dto;

import java.util.List;

/**
 * Response for {@code POST /api/confluence/query}.
 */
public record QueryResponse(
        QueryStatus status,
        String query,
        String parentPageId,
        String collectionName,
        int resultCount,
        List<QueryHit> hits,
        String message,
        String errorDetail
) {
    public static QueryResponse success(
            String query,
            String parentPageId,
            String collectionName,
            List<QueryHit> hits) {
        return new QueryResponse(
                QueryStatus.SUCCESS,
                query,
                parentPageId,
                collectionName,
                hits.size(),
                hits,
                "Query completed.",
                null);
    }

    public static QueryResponse error(String query, String parentPageId, String message, String errorDetail) {
        return new QueryResponse(
                QueryStatus.ERROR,
                query,
                parentPageId,
                null,
                0,
                List.of(),
                message,
                errorDetail);
    }
}
