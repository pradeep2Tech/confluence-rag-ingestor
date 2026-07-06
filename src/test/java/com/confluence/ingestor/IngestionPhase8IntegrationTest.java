package com.confluence.ingestor;

import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.model.PageManifestEntry;
import com.confluence.ingestor.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IngestionPhase8IntegrationTest extends IntegrationTest {

    @Autowired
    private VectorStore vectorStore;

    @Test
    void ingestVectorsLoadsChunksIntoChromaAndUpdatesManifest() throws Exception {
        String baseUrl = confluence.url("").toString().replaceAll("/$", "");
        String parentPageId = "800";
        String pageId = "801";

        PageManifest manifest = manifestService.newEmptyManifest(baseUrl, parentPageId);
        PageManifestEntry entry = PageManifestEntry.empty(pageId, "Architecture Page", baseUrl + "/display/DEV/Architecture");
        entry.setMarkdownExtracted(true);
        entry.setChunked(true);
        manifest.setPages(List.of(entry));
        manifest.setTotalPages(1);
        manifestService.saveManifest(parentPageId, manifest);

        Path chunksDir = tempDataDir.resolve(parentPageId + "/chunks");
        Files.createDirectories(chunksDir);
        Files.writeString(
                chunksDir.resolve(pageId + ".jsonl"),
                """
                        {"chunkId":"801-0","pageId":"801","parentPageId":"800","headingPath":"Overview","chunkIndex":0,"text":"Vector body","metadata":{"title":"Architecture Page"}}
                        """);

        mockMvc.perform(post("/api/confluence/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baseUrl": "%s",
                                  "parentPageId": "%s",
                                  "pat": "pat",
                                  "ingestVectors": true
                                }
                                """.formatted(baseUrl, parentPageId)))
                .andExpect(status().isAccepted());

        awaitVectorIngested(parentPageId, pageId);

        verify(vectorStore).add(any(List.class));

        PageManifestEntry updated = manifestService.loadManifest(parentPageId).getPages().getFirst();
        assertThat(updated.isVectorIngested()).isTrue();
        assertThat(updated.getVectorCollection()).isEqualTo("test-confluence-rag");
    }
}
