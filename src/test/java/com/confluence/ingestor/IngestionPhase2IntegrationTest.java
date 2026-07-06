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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IngestionPhase2IntegrationTest {

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
            // drain unconsumed mock responses between tests
        }
    }

    @Test
    void manifestCrawlBuildsManifestFromConfluenceTree() throws Exception {
        String baseUrl = confluence.url("").toString().replaceAll("/$", "");
        String parentPageId = "100";

        enqueueConfluenceTree();

        String body = """
                {
                  "baseUrl": "%s",
                  "parentPageId": "%s",
                  "pat": "test-pat",
                  "forceRebuildManifest": true
                }
                """.formatted(baseUrl, parentPageId);

        mockMvc.perform(post("/api/confluence/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.mode").value("MANIFEST_CRAWL"));

        awaitManifestPageCount(parentPageId, 4);

        PageManifest manifest = manifestService.loadManifest(parentPageId);
        List<String> pageIds = manifest.getPages().stream().map(PageManifestEntry::getPageId).toList();
        assertThat(pageIds).containsExactly("100", "101", "102", "103");

        Path crawlProgress = tempDataDir.resolve(parentPageId + "/crawl-progress.json");
        assertThat(Files.isRegularFile(crawlProgress)).isTrue();
        String progressJson = Files.readString(crawlProgress);
        assertThat(progressJson).contains("COMPLETED");
    }

    @Test
    void duplicateManifestCrawlReturnsAlreadyRunning() throws Exception {
        String baseUrl = confluence.url("").toString().replaceAll("/$", "");

        confluence.enqueue(new MockResponse()
                .setBody("""
                        {"id":"200","title":"Slow Root","_links":{"webui":"/display/SPACE/Root"}}
                        """)
                .addHeader("Content-Type", "application/json")
                .setBodyDelay(3, TimeUnit.SECONDS));
        confluence.enqueue(jsonResponse("""
                {"results":[],"_links":{}}
                """));

        mockMvc.perform(post("/api/confluence/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseUrl":"%s","parentPageId":"200","pat":"pat","forceRebuildManifest":true}
                                """.formatted(baseUrl)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/confluence/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseUrl":"%s","parentPageId":"200","pat":"pat","forceRebuildManifest":true}
                                """.formatted(baseUrl)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ALREADY_RUNNING"));
    }

    @Test
    void statusReportsCrawlProgress() throws Exception {
        String baseUrl = confluence.url("").toString().replaceAll("/$", "");
        enqueueConfluenceTree();

        mockMvc.perform(post("/api/confluence/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseUrl":"%s","parentPageId":"300","pat":"pat","forceRebuildManifest":true}
                                """.formatted(baseUrl)))
                .andExpect(status().isAccepted());

        awaitManifestPageCount("300", 4);

        mockMvc.perform(get("/api/confluence/ingest/status/300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("manifest present")));
    }

    private void enqueueConfluenceTree() {
        confluence.enqueue(jsonResponse("""
                {"id":"100","title":"Root","_links":{"webui":"/display/SPACE/Root"}}
                """));
        confluence.enqueue(jsonResponse("""
                {"results":[
                  {"id":"101","title":"Child A","_links":{"webui":"/display/SPACE/A"}},
                  {"id":"102","title":"Child B","_links":{"webui":"/display/SPACE/B"}}
                ],"_links":{}}
                """));
        confluence.enqueue(jsonResponse("""
                {"results":[],"_links":{}}
                """));
        confluence.enqueue(jsonResponse("""
                {"results":[{"id":"103","title":"Grandchild","_links":{"webui":"/display/SPACE/C"}}],"_links":{}}
                """));
        confluence.enqueue(jsonResponse("""
                {"results":[],"_links":{}}
                """));
    }

    private void awaitManifestPageCount(String parentPageId, int expectedCount) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (manifestService.manifestExists(parentPageId)) {
                PageManifest manifest = manifestService.loadManifest(parentPageId);
                if (manifest.getPages() != null && manifest.getPages().size() == expectedCount) {
                    return;
                }
            }
            Thread.sleep(100);
        }
        PageManifest manifest = manifestService.manifestExists(parentPageId)
                ? manifestService.loadManifest(parentPageId)
                : null;
        int actual = manifest != null && manifest.getPages() != null ? manifest.getPages().size() : 0;
        throw new AssertionError("Expected " + expectedCount + " pages in manifest, got " + actual);
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }
}
