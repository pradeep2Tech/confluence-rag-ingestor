package com.confluence.ingestor.confluence;

import com.confluence.ingestor.confluence.dto.ConfluenceAttachmentDto;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentClientTest {

    private MockWebServer server;
    private AttachmentClient attachmentClient;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("").toString().replaceAll("/$", "");
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer test-pat")
                .build();
        attachmentClient = new AttachmentClient(new ConfluenceClient(baseUrl, restClient));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void listAttachmentsReturnsResults() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("""
                        {"results":[{"id":"att-1","title":"diagram.png","_links":{"download":"/download/attachments/DEV/100/diagram.png"},"extensions":{"mediaType":"image/png","fileSize":128}}],"_links":{}}
                        """)
                .addHeader("Content-Type", "application/json"));

        List<ConfluenceAttachmentDto> attachments = attachmentClient.listAttachments("100");

        assertThat(attachments).hasSize(1);
        assertThat(attachments.getFirst().getTitle()).isEqualTo("diagram.png");
        assertThat(attachments.getFirst().downloadPath()).contains("diagram.png");
    }

    @Test
    void downloadAttachmentReturnsBytes() throws Exception {
        Buffer buffer = new Buffer();
        buffer.write(new byte[] {1, 2, 3, 4});
        server.enqueue(new MockResponse().setBody(buffer));

        byte[] content = attachmentClient.downloadAttachment("/download/attachments/DEV/100/diagram.png");

        assertThat(content).containsExactly(1, 2, 3, 4);
    }
}
