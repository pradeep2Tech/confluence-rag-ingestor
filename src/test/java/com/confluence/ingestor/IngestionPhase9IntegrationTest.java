package com.confluence.ingestor;

import com.confluence.ingestor.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IngestionPhase9IntegrationTest extends IntegrationTest {

    @Autowired
    private VectorStore vectorStore;

    @Test
    void queryReturnsHitsWithSourcePageLinks() throws Exception {
        Document hit = Document.builder()
                .id("701-0")
                .text("Billing overview content")
                .metadata(Map.of(
                        "chunkId", "701-0",
                        "pageId", "701",
                        "parentPageId", "700",
                        "title", "Billing",
                        "webUrl", "https://confluence.example.com/display/DEV/Billing",
                        "headingPath", "Overview",
                        "score", 0.92))
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(hit));

        mockMvc.perform(post("/api/confluence/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "billing overview",
                                  "parentPageId": "700",
                                  "topK": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.resultCount").value(1))
                .andExpect(jsonPath("$.hits[0].title").value("Billing"))
                .andExpect(jsonPath("$.hits[0].webUrl").value("https://confluence.example.com/display/DEV/Billing"))
                .andExpect(jsonPath("$.hits[0].pageId").value("701"));
    }
}
