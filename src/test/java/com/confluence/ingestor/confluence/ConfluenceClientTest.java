package com.confluence.ingestor.confluence;

import com.confluence.ingestor.confluence.dto.ConfluencePageDto;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfluenceClientTest {

    private MockWebServer server;
    private ConfluenceClient client;

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
    void getContentReturnsPage() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("""
                        {"id":"100","title":"Root","_links":{"webui":"/display/SPACE/Root"}}
                        """)
                .addHeader("Content-Type", "application/json"));

        ConfluencePageDto page = client.getContent("100", "space,version");

        assertThat(page.getId()).isEqualTo("100");
        assertThat(page.getTitle()).isEqualTo("Root");
        assertThat(client.buildWebUrl(page)).contains("/display/SPACE/Root");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).contains("/rest/api/content/100");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-pat");
    }

    @Test
    void listDirectChildrenPaginatesWithNextLinkWithoutSkipping() throws Exception {
        StringBuilder batch1 = new StringBuilder("{\"results\":[");
        for (int i = 1; i <= 100; i++) {
            if (i > 1) {
                batch1.append(',');
            }
            batch1.append("{\"id\":\"").append(i).append("\",\"title\":\"Page ").append(i).append("\"}");
        }
        batch1.append("],\"_links\":{\"next\":\"")
                .append(server.url("/rest/api/content/parent/child/page?cursor=abc").toString())
                .append("\"}}");

        server.enqueue(new MockResponse()
                .setBody(batch1.toString())
                .addHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse()
                .setBody("""
                        {"results":[{"id":"101","title":"Page 101"}],"_links":{}}
                        """)
                .addHeader("Content-Type", "application/json"));

        List<ConfluencePageDto> children = client.listDirectChildren("parent");

        assertThat(children).hasSize(101);
        assertThat(children.get(0).getId()).isEqualTo("1");
        assertThat(children.get(99).getId()).isEqualTo("100");
        assertThat(children.get(100).getId()).isEqualTo("101");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void listDirectChildrenReturnsEmptyOn404() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("not found"));

        List<ConfluencePageDto> children = client.listDirectChildren("missing");

        assertThat(children).isEmpty();
    }
}
