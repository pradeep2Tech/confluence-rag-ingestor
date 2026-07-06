package com.confluence.ingestor.port;

import com.confluence.ingestor.confluence.dto.ConfluencePageContentDto;
import com.confluence.ingestor.confluence.dto.ConfluencePageDto;

import java.util.List;

/**
 * Abstraction over Confluence REST I/O for testability and future connector extraction.
 */
public interface ConfluencePort {

    String baseUrl();

    ConfluencePageDto getContent(String pageId, String expand);

    ConfluencePageContentDto getPageContent(String pageId);

    List<ConfluencePageDto> listDirectChildren(String pageId);

    Iterable<ConfluencePageDto> iterateDirectChildren(String pageId);

    String buildWebUrl(ConfluencePageDto page);
}
