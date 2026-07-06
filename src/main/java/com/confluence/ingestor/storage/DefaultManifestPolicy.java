package com.confluence.ingestor.storage;

import com.confluence.ingestor.config.IngestorProperties;
import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.model.PageManifestEntry;
import com.confluence.ingestor.port.ManifestPolicy;
import com.confluence.ingestor.port.ManifestSummary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Retry/skip policy and pending-page rules extracted from {@link ManifestService}.
 */
@Component
public class DefaultManifestPolicy implements ManifestPolicy {

    private final IngestorProperties properties;

    public DefaultManifestPolicy(IngestorProperties properties) {
        this.properties = properties;
    }

    @Override
    public ManifestSummary summarize(PageManifest manifest) {
        List<PageManifestEntry> pages = manifest.getPages() != null ? manifest.getPages() : List.of();
        int total = pages.size();
        int ingested = 0;
        int pending = 0;
        int failed = 0;
        int skipThreshold = properties.retrySkipThreshold();

        for (PageManifestEntry page : pages) {
            if (page.isMarkdownExtracted()) {
                ingested++;
            } else if (effectiveRetryCount(page) < skipThreshold) {
                pending++;
            }
            if (page.getLastError() != null && !page.getLastError().isBlank()) {
                failed++;
            }
        }
        return new ManifestSummary(total, ingested, pending, failed);
    }

    @Override
    public int effectiveRetryCount(PageManifestEntry page) {
        if (page.getNoOfRetries() > 0 || page.getLastError() == null || page.getLastError().isBlank()) {
            return Math.max(0, page.getNoOfRetries());
        }
        return 1;
    }

    @Override
    public boolean isPendingForIngestion(PageManifestEntry page) {
        if (page.isMarkdownExtracted()) {
            return false;
        }
        return effectiveRetryCount(page) < properties.retrySkipThreshold();
    }

    @Override
    public boolean isPendingForChunking(PageManifestEntry page) {
        if (!page.isMarkdownExtracted() || page.isChunked()) {
            return false;
        }
        return effectiveRetryCount(page) < properties.retrySkipThreshold();
    }

    @Override
    public boolean isPendingForVectorIngest(PageManifestEntry page) {
        if (!page.isChunked() || page.isVectorIngested()) {
            return false;
        }
        return effectiveRetryCount(page) < properties.retrySkipThreshold();
    }
}
