package com.confluence.ingestor.storage;

import com.confluence.ingestor.confluence.ConfluenceClient;
import com.confluence.ingestor.confluence.dto.ConfluencePageDto;
import com.confluence.ingestor.config.IngestorProperties;
import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.model.PageManifestEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManifestServiceMergeTest {

    @TempDir
    Path tempDataDir;

    @Test
    void buildFullManifestPreservesIngestionStateOnRebuild() throws Exception {
        IngestorProperties properties = new IngestorProperties(
                tempDataDir.toString(),
                100,
                5,
                60,
                true,
                1,
                2,
                List.of("png", "jpg", "jpeg", "gif", "svg", "webp", "bmp"),
                List.of("drawio", "xml"),
                4000,
                true,
                "test-confluence-rag",
                true,
                5,
                0.0,
                false,
                2,
                4);

        JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        FileStorageService fileStorageService = new FileStorageService(properties, objectMapper);
        ManifestService manifestService = new ManifestService(
                fileStorageService,
                properties,
                new ManifestLockCoordinator(),
                new DefaultManifestPolicy(properties));

        String baseUrl = "https://confluence.example.com";
        String parentPageId = "500";
        String pageId = "501";

        PageManifestEntry existing = PageManifestEntry.empty(pageId, "Old Title", baseUrl + "/display/DEV/Old");
        existing.setMarkdownExtracted(true);
        existing.setMarkdownPath("pages/501/page.md");
        existing.setMetadataPath("pages/501/metadata.json");
        existing.setAssetsDirectory("pages/501/assets");
        existing.setChunked(true);
        existing.setChunksPath("chunks/501.jsonl");
        existing.setVectorIngested(true);
        existing.setVectorCollection("test-confluence-rag");
        existing.setNoOfRetries(1);

        PageManifest manifest = manifestService.newEmptyManifest(baseUrl, parentPageId);
        manifest.setPages(List.of(existing));
        manifestService.saveManifest(parentPageId, manifest);

        ConfluenceClient client = mock(ConfluenceClient.class);
        when(client.buildWebUrl(any(ConfluencePageDto.class)))
                .thenAnswer(invocation -> {
                    ConfluencePageDto page = invocation.getArgument(0);
                    String webui = page.getLinks() != null ? page.getLinks().getOrDefault("webui", "") : "";
                    return baseUrl + webui;
                });

        ConfluencePageDto parentDoc = pageDto(parentPageId, "Root", "/display/DEV/Root");
        ConfluencePageDto childDoc = pageDto(pageId, "Refreshed Title", "/display/DEV/Refreshed");

        PageManifest rebuilt = manifestService.buildFullManifest(
                baseUrl, parentPageId, parentDoc, List.of(childDoc), client);

        PageManifestEntry mergedChild = manifestService.findPageEntry(rebuilt, pageId);
        assertThat(mergedChild).isNotNull();
        assertThat(mergedChild.getTitle()).isEqualTo("Refreshed Title");
        assertThat(mergedChild.isMarkdownExtracted()).isTrue();
        assertThat(mergedChild.getMarkdownPath()).isEqualTo("pages/501/page.md");
        assertThat(mergedChild.getMetadataPath()).isEqualTo("pages/501/metadata.json");
        assertThat(mergedChild.getAssetsDirectory()).isEqualTo("pages/501/assets");
        assertThat(mergedChild.isChunked()).isTrue();
        assertThat(mergedChild.getChunksPath()).isEqualTo("chunks/501.jsonl");
        assertThat(mergedChild.isVectorIngested()).isTrue();
        assertThat(mergedChild.getVectorCollection()).isEqualTo("test-confluence-rag");
        assertThat(mergedChild.getNoOfRetries()).isEqualTo(1);
    }

    private static ConfluencePageDto pageDto(String id, String title, String webui) {
        ConfluencePageDto page = new ConfluencePageDto();
        page.setId(id);
        page.setTitle(title);
        page.setLinks(Map.of("webui", webui));
        return page;
    }
}
