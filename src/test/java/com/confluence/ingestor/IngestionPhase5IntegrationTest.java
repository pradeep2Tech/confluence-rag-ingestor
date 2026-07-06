package com.confluence.ingestor;

import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.model.PageManifestEntry;
import com.confluence.ingestor.storage.ManifestService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IngestionPhase5IntegrationTest {

    @TempDir
    static Path tempDataDir;

    private static MockWebServer confluence;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        confluence = new MockWebServer();
        confluence.start();
        registry.add("confluence.ingestor.data-directory", () -> tempDataDir.toString());
        registry.add("confluence.ingestor.verify-ssl", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ManifestService manifestService;

    @BeforeEach
    void resetDispatcher() {
        confluence.setDispatcher(new okhttp3.mockwebserver.QueueDispatcher());
    }

    @AfterEach
    void drainServer() throws Exception {
        RecordedRequest request;
        while ((request = confluence.takeRequest(100, TimeUnit.MILLISECONDS)) != null) {
            // drain
        }
    }

    @Test
    void extractMarkdownWritesComplexTableArtifacts() throws Exception {
        String baseUrl = confluence.url("").toString().replaceAll("/$", "");
        String parentPageId = "700";
        String pageId = "701";

        seedManifest(parentPageId, baseUrl, pageId, "Table Page");

        String storageHtml = """
                <p>Matrix:</p>
                <table>
                  <tr><th>Row</th><th colspan="2">Values</th></tr>
                  <tr><td>one</td><td>a</td><td>b</td></tr>
                </table>
                """;

        confluence.enqueue(pageContentResponse(pageId, "Table Page", storageHtml, "DEV"));

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

        Path tableJson = tempDataDir.resolve(parentPageId + "/pages/" + pageId + "/assets/tables/table-1.json");
        Path markdown = tempDataDir.resolve(parentPageId + "/pages/" + pageId + "/page.md");
        Path metadata = tempDataDir.resolve(parentPageId + "/pages/" + pageId + "/metadata.json");

        assertThat(Files.isRegularFile(tableJson)).isTrue();
        assertThat(Files.readString(tableJson)).contains("\"tableId\" : \"table-1\"");
        assertThat(Files.readString(tableJson)).contains("\"colspan\" : 2");
        assertThat(Files.readString(markdown)).contains("[TABLE:table-1](assets/tables/table-1.json)");
        assertThat(Files.readString(metadata)).contains("\"tableId\" : \"table-1\"");
        assertThat(Files.readString(metadata)).contains("\"complex\" : true");
    }

    private void seedManifest(String parentPageId, String baseUrl, String pageId, String title)
            throws Exception {
        PageManifest manifest = manifestService.newEmptyManifest(baseUrl, parentPageId);
        manifest.setPages(List.of(PageManifestEntry.empty(pageId, title, baseUrl + "/display/DEV/Table")));
        manifest.setTotalPages(1);
        manifestService.saveManifest(parentPageId, manifest);
    }

    private static MockResponse pageContentResponse(String pageId, String title, String storageHtml, String spaceKey) {
        String body = """
                {
                  "id":"%s",
                  "title":"%s",
                  "body":{"storage":{"value":"%s","representation":"storage"}},
                  "version":{"number":3},
                  "space":{"key":"%s","name":"Dev Space"},
                  "ancestors":[{"id":"1","title":"Root"}],
                  "_links":{"webui":"/display/%s/Table"}
                }
                """
                .formatted(pageId, title, storageHtml.replace("\"", "\\\"").replace("\n", ""), spaceKey, spaceKey);
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }

    private void awaitMarkdownExtracted(String parentPageId, String pageId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            PageManifest manifest = manifestService.loadManifest(parentPageId);
            PageManifestEntry entry = manifest.getPages().stream()
                    .filter(page -> pageId.equals(page.getPageId()))
                    .findFirst()
                    .orElse(null);
            if (entry != null && entry.isMarkdownExtracted()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for markdown extraction");
    }
}
