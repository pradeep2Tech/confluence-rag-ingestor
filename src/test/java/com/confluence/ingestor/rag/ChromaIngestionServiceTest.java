package com.confluence.ingestor.rag;

import com.confluence.ingestor.model.ChunkDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChromaIngestionServiceTest {

    @Test
    void ingestChunksMapsMetadataAndCallsVectorStore() {
        VectorStore vectorStore = mock(VectorStore.class);
        ChromaIngestionService service = new ChromaIngestionService(vectorStore);

        ChunkDocument chunk = new ChunkDocument();
        chunk.setChunkId("801-0");
        chunk.setPageId("801");
        chunk.setParentPageId("800");
        chunk.setHeadingPath("Overview");
        chunk.setChunkIndex(0);
        chunk.setText("Vector ingest body");
        chunk.setMetadata(Map.of("title", "Architecture Page", "webUrl", "https://c.example.com/801"));

        int ingested = service.ingestChunks(List.of(chunk));

        assertThat(ingested).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        Document document = captor.getValue().getFirst();
        assertThat(document.getId()).isEqualTo("801-0");
        assertThat(document.getText()).isEqualTo("Vector ingest body");
        assertThat(document.getMetadata())
                .containsEntry("chunkId", "801-0")
                .containsEntry("pageId", "801")
                .containsEntry("parentPageId", "800")
                .containsEntry("headingPath", "Overview")
                .containsEntry("chunkIndex", 0)
                .containsEntry("title", "Architecture Page")
                .containsEntry("webUrl", "https://c.example.com/801");
    }
}
