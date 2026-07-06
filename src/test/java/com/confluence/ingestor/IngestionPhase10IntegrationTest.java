package com.confluence.ingestor;

import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.model.PageManifestEntry;
import com.confluence.ingestor.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IngestionPhase10IntegrationTest extends IntegrationTest {

    @Test
    void statusEndpointReportsBatchProgressFields() throws Exception {
        String baseUrl = confluence.url("").toString().replaceAll("/$", "");
        String parentPageId = "820";
        String pageId = "821";

        seedManifest(parentPageId, baseUrl, pageId, "Status Page");
        confluence.enqueue(pageContentResponse(pageId, "Status Page", "<p>Status body</p>", "DEV"));

        mockMvc.perform(post("/api/confluence/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baseUrl": "%s",
                                  "parentPageId": "%s",
                                  "pat": "pat",
                                  "extractMarkdown": true
                                }
                                """.formatted(baseUrl, parentPageId)))
                .andExpect(status().isAccepted());

        awaitMarkdownExtracted(parentPageId, pageId);
        awaitBatchProgressStatus(parentPageId, "PAGE_TRANSFORM", "COMPLETED");

        mockMvc.perform(get("/api/confluence/ingest/status/" + parentPageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchProgressPath").isNotEmpty())
                .andExpect(jsonPath("$.batchProgress.phase").value("PAGE_TRANSFORM"))
                .andExpect(jsonPath("$.batchProgress.status").value("COMPLETED"))
                .andExpect(jsonPath("$.message", containsString("batch(PAGE_TRANSFORM)=COMPLETED")));
    }

    @Test
    void batchProgressWrittenOnPageTransform() throws Exception {
        String baseUrl = confluence.url("").toString().replaceAll("/$", "");
        String parentPageId = "810";
        String pageId = "811";

        seedManifest(parentPageId, baseUrl, pageId, "Progress Page");
        confluence.enqueue(pageContentResponse(pageId, "Progress Page", "<p>Progress body</p>", "DEV"));

        mockMvc.perform(post("/api/confluence/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baseUrl": "%s",
                                  "parentPageId": "%s",
                                  "pat": "pat",
                                  "extractMarkdown": true
                                }
                                """.formatted(baseUrl, parentPageId)))
                .andExpect(status().isAccepted());

        awaitMarkdownExtracted(parentPageId, pageId);

        Path batchProgress = tempDataDir.resolve(parentPageId + "/batch-progress.json");
        assertThat(Files.isRegularFile(batchProgress)).isTrue();
        assertThat(Files.readString(batchProgress)).contains("PAGE_TRANSFORM");
        assertThat(Files.readString(batchProgress)).contains("COMPLETED");
    }

    @Test
    void manifestRebuildPreservesMarkdownState() throws Exception {
        String baseUrl = confluence.url("").toString().replaceAll("/$", "");
        String parentPageId = "830";
        String pageId = "831";

        PageManifestEntry entry = PageManifestEntry.empty(pageId, "Docs", baseUrl + "/display/DEV/Docs");
        entry.setMarkdownExtracted(true);
        entry.setMarkdownPath("pages/831/page.md");
        entry.setChunked(true);
        entry.setChunksPath("chunks/831.jsonl");

        PageManifest manifest = manifestService.newEmptyManifest(baseUrl, parentPageId);
        manifest.setPages(List.of(entry));
        manifest.setTotalPages(1);
        manifestService.saveManifest(parentPageId, manifest);

        Path pageMd = tempDataDir.resolve(parentPageId + "/pages/" + pageId + "/page.md");
        Files.createDirectories(pageMd.getParent());
        Files.writeString(pageMd, "# Docs\n\nPreserved content");

        confluence.enqueue(jsonResponse(
                """
                        {"id":"830","title":"Root","_links":{"webui":"/display/DEV/Root"}}
                        """));
        confluence.enqueue(jsonResponse("""
                {"results":[],"_links":{}}
                """));

        mockMvc.perform(post("/api/confluence/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baseUrl": "%s",
                                  "parentPageId": "%s",
                                  "pat": "pat",
                                  "forceRebuildManifest": true
                                }
                                """.formatted(baseUrl, parentPageId)))
                .andExpect(status().isAccepted());

        awaitManifestPageCount(parentPageId, 1);

        PageManifestEntry rebuilt = manifestService.loadManifest(parentPageId).getPages().stream()
                .filter(page -> pageId.equals(page.getPageId()))
                .findFirst()
                .orElseThrow();
        assertThat(rebuilt.isMarkdownExtracted()).isTrue();
        assertThat(rebuilt.getMarkdownPath()).isEqualTo("pages/831/page.md");
        assertThat(rebuilt.isChunked()).isTrue();
        assertThat(rebuilt.getChunksPath()).isEqualTo("chunks/831.jsonl");
    }
}
