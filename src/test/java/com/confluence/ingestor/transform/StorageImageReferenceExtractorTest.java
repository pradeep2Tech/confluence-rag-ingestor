package com.confluence.ingestor.transform;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StorageImageReferenceExtractorTest {

    @Test
    void extractsAttachmentMacroAndImgSrcFilenames() {
        String html = """
                <p>See diagram:</p>
                <ac:image>
                  <ri:attachment ri:filename="diagram.png"/>
                </ac:image>
                <img src="/download/attachments/DEV/501/photo.jpg?version=1"/>
                """;

        Set<String> filenames = StorageImageReferenceExtractor.extractReferencedFilenames(html);

        assertThat(filenames).containsExactly("diagram.png", "photo.jpg");
    }
}
