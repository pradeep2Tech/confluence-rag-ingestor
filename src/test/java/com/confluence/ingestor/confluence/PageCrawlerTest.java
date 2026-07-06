package com.confluence.ingestor.confluence;

import com.confluence.ingestor.confluence.dto.ConfluencePageDto;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageCrawlerTest {

    private MockWebServer server;
    private ConfluenceClient client;
    private final PageCrawler pageCrawler = new PageCrawler();

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("").toString().replaceAll("/$", "");
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer test-pat")
                .build();
        client = new ConfluenceClient(baseUrl, restClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void fetchAllDescendantPagesWalksDepthFirst() throws Exception {
        server.enqueue(json("""
                {"id":"100","title":"Root","_links":{"webui":"/display/SPACE/Root"}}
                """));
        server.enqueue(json("""
                {"results":[
                  {"id":"101","title":"Child A","_links":{"webui":"/display/SPACE/A"}},
                  {"id":"102","title":"Child B","_links":{"webui":"/display/SPACE/B"}}
                ],"_links":{}}
                """));
        server.enqueue(json("""
                {"results":[],"_links":{}}
                """));
        server.enqueue(json("""
                {"results":[{"id":"103","title":"Grandchild","_links":{"webui":"/display/SPACE/C"}}],"_links":{}}
                """));
        server.enqueue(json("""
                {"results":[],"_links":{}}
                """));

        ConfluencePageDto parent = client.getContent("100", "space,version");
        List<ConfluencePageDto> descendants =
                pageCrawler.fetchAllDescendantPages(client, "100", parent.getTitle(), null);

        assertThat(descendants).hasSize(3);
        assertThat(descendants.stream().map(ConfluencePageDto::getId).toList())
                .containsExactly("101", "102", "103");
        assertThat(server.getRequestCount()).isEqualTo(5);
    }

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }
}
