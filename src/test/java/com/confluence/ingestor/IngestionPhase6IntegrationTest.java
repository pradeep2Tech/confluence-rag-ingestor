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
class IngestionPhase6IntegrationTest {

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
    void extractMarkdownWritesDrawioDiagramArtifacts() throws Exception {
        String baseUrl = confluence.url("").toString().replaceAll("/$", "");
        String parentPageId = "800";
        String pageId = "801";

        seedManifest(parentPageId, baseUrl, pageId, "Architecture Page");

        String storageHtml = """
                <p>System context:</p>
                <ac:structured-macro ac:name="drawio">
                  <ac:parameter ac:name="diagramName">Architecture</ac:parameter>
                  <ac:plain-text-body><mxfile><diagram><mxGraphModel><root>
                  <mxCell id="2" value="API Gateway" vertex="1" parent="1"/>
                  <mxCell id="3" value="Database" vertex="1" parent="1"/>
                  </root></mxGraphModel></diagram></mxfile></ac:plain-text-body>
                </ac:structured-macro>
                """;

        confluence.enqueue(pageContentResponse(pageId, "Architecture Page", storageHtml, "DEV"));

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

        Path drawioFile = tempDataDir.resolve(parentPageId + "/pages/" + pageId + "/assets/diagrams/diagram-1.drawio");
        Path diagramJson = tempDataDir.resolve(parentPageId + "/pages/" + pageId + "/assets/diagrams/diagram-1.json");
        Path markdown = tempDataDir.resolve(parentPageId + "/pages/" + pageId + "/page.md");
        Path metadata = tempDataDir.resolve(parentPageId + "/pages/" + pageId + "/metadata.json");

        assertThat(Files.isRegularFile(drawioFile)).isTrue();
        assertThat(Files.readString(drawioFile)).contains("<mxfile>");
        assertThat(Files.isRegularFile(diagramJson)).isTrue();
        assertThat(Files.readString(diagramJson)).contains("\"diagramName\" : \"Architecture\"");
        assertThat(Files.readString(diagramJson)).contains("API Gateway");
        assertThat(Files.readString(markdown)).contains("[DIAGRAM:diagram-1](assets/diagrams/diagram-1.json)");
        assertThat(Files.readString(markdown)).contains("- API Gateway");
        assertThat(Files.readString(markdown)).contains("- Database");
        assertThat(Files.readString(metadata)).contains("\"diagramId\" : \"diagram-1\"");
        assertThat(Files.readString(metadata)).contains("\"labelCount\" : 2");
    }

    private void seedManifest(String parentPageId, String baseUrl, String pageId, String title)
            throws Exception {
        PageManifest manifest = manifestService.newEmptyManifest(baseUrl, parentPageId);
        manifest.setPages(List.of(PageManifestEntry.empty(pageId, title, baseUrl + "/display/DEV/Architecture")));
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
                  "_links":{"webui":"/display/%s/Architecture"}
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
