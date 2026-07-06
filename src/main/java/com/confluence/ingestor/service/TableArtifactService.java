package com.confluence.ingestor.service;

import com.confluence.ingestor.model.PageTableDocument;
import com.confluence.ingestor.storage.FileStorageService;
import com.confluence.ingestor.transform.ExtractedTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists extracted table JSON artifacts under {@code assets/tables/}.
 */
@Service
public class TableArtifactService {

    private static final Logger log = LoggerFactory.getLogger(TableArtifactService.class);

    private final FileStorageService fileStorageService;

    public TableArtifactService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public List<PageTableDocument> writeTableArtifacts(
            String parentPageId, String pageId, List<ExtractedTable> tables) throws IOException {
        if (tables == null || tables.isEmpty()) {
            return List.of();
        }
        Files.createDirectories(fileStorageService.pageTablesDirectory(parentPageId, pageId));

        List<PageTableDocument> documents = new ArrayList<>();
        for (ExtractedTable table : tables) {
            var tablePath = fileStorageService.pageTablePath(parentPageId, pageId, table.fileName());
            fileStorageService.writeJsonAtomic(tablePath, table);
            documents.add(PageTableDocument.of(
                    table.tableId(),
                    table.fileName(),
                    fileStorageService.displayPath(tablePath),
                    table.rowCount(),
                    table.columnCount(),
                    table.complex()));
        }
        log.debug("Wrote {} table artifacts parentPageId={} pageId={}", documents.size(), parentPageId, pageId);
        return documents;
    }
}
