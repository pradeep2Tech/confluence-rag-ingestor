package com.confluence.ingestor.rag;

import com.confluence.ingestor.model.ChunkDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownChunkerTest {

    private MarkdownChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new MarkdownChunker();
    }

    @Test
    void chunksByHeadingPath() {
        String markdown = """
                # Intro

                Hello intro body.

                ## Details

                More detail text.
                """;

        List<ChunkDocument> chunks = chunker.chunk(markdown, "900", "901", Map.of("title", "Chunk Page"), 4000);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getHeadingPath()).isEqualTo("Intro");
        assertThat(chunks.get(0).getText()).contains("Hello intro body");
        assertThat(chunks.get(1).getHeadingPath()).isEqualTo("Intro > Details");
        assertThat(chunks.get(1).getText()).contains("More detail text");
        assertThat(chunks.get(0).getChunkId()).isEqualTo("901-0");
        assertThat(chunks.get(1).getChunkId()).isEqualTo("901-1");
    }

    @Test
    void splitsOversizedSections() {
        String oversized = "word ".repeat(900).strip();

        List<ChunkDocument> chunks = chunker.chunk(oversized, "900", "901", null, 4000);

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allMatch(chunk -> chunk.getText().length() <= 4000);
    }
}
