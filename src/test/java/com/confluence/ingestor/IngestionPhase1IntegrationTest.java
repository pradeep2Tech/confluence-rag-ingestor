package com.confluence.ingestor;

import com.confluence.ingestor.api.dto.IngestionJobStatus;
import com.confluence.ingestor.api.dto.IngestionMode;
import com.confluence.ingestor.api.dto.IngestionRequest;
import com.confluence.ingestor.api.dto.IngestionResponse;
import com.confluence.ingestor.service.IngestionService;
import com.confluence.ingestor.storage.ManifestService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IngestionPhase1IntegrationTest {

    @TempDir
    static Path tempDataDir;

    @DynamicPropertySource
    static void dataDirectory(DynamicPropertyRegistry registry) {
        registry.add("confluence.ingestor.data-directory", () -> tempDataDir.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private ManifestService manifestService;

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void ingestCreatesManifestAndDataDirectory() throws Exception {
        String body = """
                {
                  "baseUrl": "https://confluence.example.com",
                  "parentPageId": "999001",
                  "pat": "test-pat-not-logged"
                }
                """;

        mockMvc.perform(post("/api/confluence/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.mode").value("MANIFEST_INIT"))
                .andExpect(jsonPath("$.totalPages").value(0));

        Path manifest = tempDataDir.resolve("999001/manifest.json");
        assertThat(Files.isRegularFile(manifest)).isTrue();
        assertThat(manifestService.loadManifest("999001").getPages()).isEmpty();
    }

    @Test
    void statusReadsLocalManifest() throws Exception {
        IngestionResponse response = ingestionService.startIngestion(
                new IngestionRequest("https://c.example.com", "888002", "pat", false, null, null, null, null, null, null));
        assertThat(response.status()).isEqualTo(IngestionJobStatus.SUCCESS);

        mockMvc.perform(get("/api/confluence/ingest/status/888002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manifestExists").value(true))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void forceRebuildReturnsAccepted() throws Exception {
        ingestionService.startIngestion(
                new IngestionRequest("https://c.example.com", "777003", "pat", false, null, null, null, null, null, null));

        IngestionResponse accepted = ingestionService.startIngestion(
                new IngestionRequest("https://c.example.com", "777003", "pat", true, null, null, null, null, null, null));

        assertThat(accepted.status()).isEqualTo(IngestionJobStatus.ACCEPTED);
        assertThat(accepted.mode()).isEqualTo(IngestionMode.MANIFEST_CRAWL);
    }
}
