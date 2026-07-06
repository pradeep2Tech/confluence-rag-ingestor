package com.confluence.ingestor.confluence;

import com.confluence.ingestor.confluence.dto.ConfluencePageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * Depth-first descendant page discovery via repeated {@code /child/page} calls.
 * <p>
 * Many Confluence DC builds return 501 for {@code /descendant/page}; this mirrors the Python POC walk.
 */
@Component
public class PageCrawler {

    private static final Logger log = LoggerFactory.getLogger(PageCrawler.class);

    /**
     * Callback after each node whose direct children were listed.
     *
     * @param processedPages       count of completed child-listings
     * @param totalPagesDiscovered 1 + number of descendants found so far (parent counts as 1)
     * @param queueSize            remaining stack depth
     * @param currentPageId        page whose children were just listed
     * @param currentTitle         title for {@code currentPageId}
     */
    @FunctionalInterface
    public interface CrawlProgressCallback {
        void onTick(
                int processedPages,
                int totalPagesDiscovered,
                int queueSize,
                String currentPageId,
                String currentTitle);
    }

    /**
     * All descendant pages under {@code parentPageId} (parent row excluded — caller adds it).
     * Order: each direct child of the parent is listed, then that child's entire subtree
     * depth-first before the next sibling.
     */
    public List<ConfluencePageDto> fetchAllDescendantPages(
            ConfluenceClient client,
            String parentPageId,
            String parentTitle,
            CrawlProgressCallback progress) {
        log.info(
                "Fetching descendant tree under parentPageId={} (sequential Confluence API calls).",
                parentPageId);

        List<ConfluencePageDto> descendants = new ArrayList<>();
        Map<String, String> titles = new HashMap<>();
        titles.put(parentPageId, parentTitle != null ? parentTitle : "");

        List<String> stack = new ArrayList<>();
        stack.add(parentPageId);
        int expandCount = 0;

        while (!stack.isEmpty()) {
            String pageId = stack.removeLast();
            expandCount++;

            List<ConfluencePageDto> children = client.listDirectChildren(pageId);
            for (ConfluencePageDto child : children) {
                String childId = child.getId();
                if (childId == null || childId.isBlank()) {
                    continue;
                }
                descendants.add(child);
                titles.put(childId, child.getTitle() != null ? child.getTitle() : "");
            }
            for (int i = children.size() - 1; i >= 0; i--) {
                ConfluencePageDto child = children.get(i);
                String childId = child.getId();
                if (childId != null && !childId.isBlank()) {
                    stack.add(childId);
                }
            }

            int totalDiscovered = 1 + descendants.size();
            int queueSize = stack.size();
            if (progress != null) {
                progress.onTick(
                        expandCount,
                        totalDiscovered,
                        queueSize,
                        pageId,
                        titles.getOrDefault(pageId, null));
            }
        }

        return descendants;
    }
}
