package com.confluence.ingestor.transform;

import org.jsoup.nodes.Element;

/**
 * One draw.io macro reference found in Confluence {@code body.storage} XHTML.
 */
public record DrawioReference(
        String diagramName,
        String attachmentFilename,
        String embeddedXml,
        Element macroElement) {
}
