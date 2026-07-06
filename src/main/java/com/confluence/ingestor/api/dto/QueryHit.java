package com.confluence.ingestor.api.dto;

/**
 * One semantic-search hit with Confluence source links.
 */
public record QueryHit(
        String chunkId,
        String pageId,
        String parentPageId,
        String title,
        String webUrl,
        String headingPath,
        String spaceKey,
        Integer version,
        String text,
        Double score,
        Double distance
) {
}
