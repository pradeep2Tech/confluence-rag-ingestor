package com.confluence.ingestor.support;

import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.model.PageManifestEntry;
import com.confluence.ingestor.storage.ManifestService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestAiConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class IntegrationTest {

    @TempDir
    protected static Path tempDataDir;

    protected static MockWebServer confluence;

    @DynamicPropertySource
    static void baseProperties(DynamicPropertyRegistry registry) throws Exception {
        confluence = new MockWebServer();
        confluence.start();
        registry.add("confluence.ingestor.data-directory", () -> tempDataDir.toString());
        registry.add("confluence.ingestor.verify-ssl", () -> "true");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ManifestService manifestService;

    @BeforeEach
    void resetMockWebServer() {
        confluence.setDispatcher(new QueueDispatcher());
    }

    @AfterEach
    void drainMockWebServer() throws Exception {
        RecordedRequest request;
        while ((request = confluence.takeRequest(100, TimeUnit.MILLISECONDS)) != null) {
            // drain unconsumed mock responses between tests
        }
    }

    protected void seedManifest(String parentPageId, String baseUrl, String pageId, String title)
            throws Exception {
        PageManifest manifest = manifestService.newEmptyManifest(baseUrl, parentPageId);
        manifest.setPages(List.of(PageManifestEntry.empty(pageId, title, baseUrl + "/display/DEV/" + title)));
        manifest.setTotalPages(1);
        manifestService.saveManifest(parentPageId, manifest);
    }

    protected static MockResponse pageContentResponse(
            String pageId, String title, String storageHtml, String spaceKey) {
        String body = """
                {
                  "id":"%s",
                  "title":"%s",
                  "body":{"storage":{"value":"%s","representation":"storage"}},
                  "version":{"number":3},
                  "space":{"key":"%s","name":"Dev Space"},
                  "ancestors":[{"id":"1","title":"Root"}],
                  "_links":{"webui":"/display/%s/%s"}
                }
                """
                .formatted(
                        pageId,
                        title,
                        storageHtml.replace("\"", "\\\"").replace("\n", ""),
                        spaceKey,
                        spaceKey,
                        title.replace(" ", ""));
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }

    protected static MockResponse jsonResponse(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }

    protected void awaitBatchProgressStatus(String parentPageId, String expectedPhase, String expectedStatus)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            Path batchProgress = tempDataDir.resolve(parentPageId + "/batch-progress.json");
            if (Files.isRegularFile(batchProgress)) {
                String json = Files.readString(batchProgress);
                if (json.contains(expectedPhase) && json.contains(expectedStatus)) {
                    return;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError(
                "Timed out waiting for batch progress phase="
                        + expectedPhase
                        + " status="
                        + expectedStatus
                        + " for parentPageId="
                        + parentPageId);
    }

    protected void awaitMarkdownExtracted(String parentPageId, String pageId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
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

    protected void awaitChunked(String parentPageId, String pageId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            PageManifest manifest = manifestService.loadManifest(parentPageId);
            PageManifestEntry entry = manifest.getPages().stream()
                    .filter(page -> pageId.equals(page.getPageId()))
                    .findFirst()
                    .orElse(null);
            if (entry != null && entry.isChunked()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for chunking");
    }

    protected void awaitVectorIngested(String parentPageId, String pageId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            PageManifest manifest = manifestService.loadManifest(parentPageId);
            PageManifestEntry entry = manifest.getPages().stream()
                    .filter(page -> pageId.equals(page.getPageId()))
                    .findFirst()
                    .orElse(null);
            if (entry != null && entry.isVectorIngested()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for vector ingest");
    }

    protected void awaitManifestPageCount(String parentPageId, int expectedCount) throws Exception {
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
}
