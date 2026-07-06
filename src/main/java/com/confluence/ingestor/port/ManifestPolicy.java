package com.confluence.ingestor.port;

import com.confluence.ingestor.model.PageManifest;
import com.confluence.ingestor.model.PageManifestEntry;

/**
 * Business rules for manifest page state — retry/skip policy and pending checks.
 */
public interface ManifestPolicy {

    ManifestSummary summarize(PageManifest manifest);

    int effectiveRetryCount(PageManifestEntry page);

    boolean isPendingForIngestion(PageManifestEntry page);

    boolean isPendingForChunking(PageManifestEntry page);

    boolean isPendingForVectorIngest(PageManifestEntry page);
}
