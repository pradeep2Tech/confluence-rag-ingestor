package com.confluence.ingestor;

import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.model.PageManifestEntry;
import com.confluence.ingestor.storage.ManifestService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IngestionPhase4IntegrationTest {

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
    void extractMarkdownDownloadsReferencedImageAssets() throws Exception {
        String baseUrl = confluence.url("").toString().replaceAll("/$", "");
        String parentPageId = "600";
        String pageId = "601";

        seedManifest(parentPageId, baseUrl, pageId, "Diagram Page");

        String storageHtml =
                "<ac:image><ri:attachment ri:filename=\"diagram.png\"/></ac:image>";

        confluence.enqueue(pageContentResponse(pageId, "Diagram Page", storageHtml, "DEV"));
        confluence.enqueue(new MockResponse()
                .setBody("""
                        {"results":[{"id":"att-9","title":"diagram.png","_links":{"download":"/download/attachments/DEV/601/diagram.png"},"extensions":{"mediaType":"image/png","fileSize":4}}],"_links":{}}
                        """)
                .addHeader("Content-Type", "application/json"));
        Buffer imageBody = new Buffer();
        imageBody.write(new byte[] {10, 20, 30, 40});
        confluence.enqueue(new MockResponse().setBody(imageBody));

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

        Path asset = tempDataDir.resolve(parentPageId + "/pages/" + pageId + "/assets/diagram.png");
        Path markdown = tempDataDir.resolve(parentPageId + "/pages/" + pageId + "/page.md");
        Path metadata = tempDataDir.resolve(parentPageId + "/pages/" + pageId + "/metadata.json");

        assertThat(Files.isRegularFile(asset)).isTrue();
        assertThat(Files.readAllBytes(asset)).containsExactly(10, 20, 30, 40);
        assertThat(Files.readString(markdown)).contains("![diagram.png](assets/diagram.png)");
        assertThat(Files.readString(metadata)).contains("\"fileName\" : \"diagram.png\"");
    }

    private void seedManifest(String parentPageId, String baseUrl, String pageId, String title)
            throws Exception {
        PageManifest manifest = manifestService.newEmptyManifest(baseUrl, parentPageId);
        manifest.setPages(List.of(PageManifestEntry.empty(pageId, title, baseUrl + "/display/DEV/Diagram")));
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
                  "_links":{"webui":"/display/%s/Diagram"}
                }
                """
                .formatted(pageId, title, storageHtml.replace("\"", "\\\""), spaceKey, spaceKey);
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
