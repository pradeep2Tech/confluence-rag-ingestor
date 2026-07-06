package com.confluence.ingestor;

import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.model.PageManifestEntry;
import com.confluence.ingestor.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IngestionPhase7IntegrationTest extends IntegrationTest {

    @Test
    void chunkMarkdownWritesJsonlArtifacts() throws Exception {
        String baseUrl = confluence.url("").toString().replaceAll("/$", "");
        String parentPageId = "900";
        String pageId = "901";

        PageManifest manifest = manifestService.newEmptyManifest(baseUrl, parentPageId);
        PageManifestEntry entry = PageManifestEntry.empty(pageId, "Chunk Page", baseUrl + "/display/DEV/Chunk");
        entry.setMarkdownExtracted(true);
        manifest.setPages(java.util.List.of(entry));
        manifest.setTotalPages(1);
        manifestService.saveManifest(parentPageId, manifest);

        Path pageDir = tempDataDir.resolve(parentPageId + "/pages/" + pageId);
        Files.createDirectories(pageDir);
        Files.writeString(
                pageDir.resolve("page.md"),
                """
                        # Section One

                        Alpha body.

                        ## Section Two

                        Beta body.
                        """);

        mockMvc.perform(post("/api/confluence/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baseUrl": "%s",
                                  "parentPageId": "%s",
                                  "pat": "pat",
                                  "chunkMarkdown": true
                                }
                                """.formatted(baseUrl, parentPageId)))
                .andExpect(status().isAccepted());

        awaitChunked(parentPageId, pageId);

        Path chunksFile = tempDataDir.resolve(parentPageId + "/chunks/" + pageId + ".jsonl");
        assertThat(Files.isRegularFile(chunksFile)).isTrue();
        String jsonl = Files.readString(chunksFile);
        assertThat(jsonl).contains("\"chunkId\"");
        assertThat(jsonl.lines().filter(line -> !line.isBlank()).count()).isEqualTo(2);

        PageManifestEntry updated = manifestService.loadManifest(parentPageId).getPages().getFirst();
        assertThat(updated.isChunked()).isTrue();
        assertThat(updated.getChunksPath()).isNotBlank();
    }
}
