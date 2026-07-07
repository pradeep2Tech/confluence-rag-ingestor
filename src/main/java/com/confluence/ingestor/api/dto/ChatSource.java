package com.confluence.ingestor.api.dto;

import java.util.List;

/**
 * One source citation returned with a chat answer.
 */
public record ChatSource(
        String title,
        String webUrl,
        String headingPath,
        Double score,
        String excerpt
) {
}
