package com.confluence.ingestor.port;

import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.model.PageManifestEntry;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Persistence port for {@code manifest.json} — I/O and structural operations only.
 */
public interface ManifestRepository {

    boolean manifestExists(String parentPageId);

    String manifestDisplayPath(String parentPageId);

    PageManifest loadManifest(String parentPageId) throws IOException;

    boolean createEmptyManifestIfMissing(String baseUrl, String parentPageId) throws IOException;

    PageManifest newEmptyManifest(String baseUrl, String parentPageId);

    void saveManifest(String parentPageId, PageManifest manifest) throws IOException;

    PageManifest mutateManifest(String parentPageId, Consumer<PageManifest> mutator) throws IOException;

    PageManifestEntry findPageEntry(PageManifest manifest, String pageId);
}
