package com.confluence.ingestor.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChromaQueryServiceTest {

    @Test
    void searchAppliesParentPageFilterAndTopK() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        ChromaQueryService service = new ChromaQueryService(vectorStore);

        service.search("billing docs", "700", 3, 0.25);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());

        SearchRequest request = captor.getValue();
        assertThat(request.getQuery()).isEqualTo("billing docs");
        assertThat(request.getTopK()).isEqualTo(3);
        assertThat(request.getSimilarityThreshold()).isEqualTo(0.25);
        assertThat(request.getFilterExpression()).isNotNull();
        assertThat(request.getFilterExpression()).asString().contains("parentPageId");
    }

    @Test
    void searchWithoutParentPageSkipsFilter() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        ChromaQueryService service = new ChromaQueryService(vectorStore);

        service.search("billing docs", null, 5, 0.0);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());

        SearchRequest request = captor.getValue();
        assertThat(request.getTopK()).isEqualTo(5);
        assertThat(request.getFilterExpression()).isNull();
    }
}
