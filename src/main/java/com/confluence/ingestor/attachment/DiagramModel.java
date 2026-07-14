package com.confluence.ingestor.attachment;

import java.util.List;

public record DiagramModel(
        String attachmentId,
        String diagramName,
        AttachmentType diagramType,
        List<DiagramNode> nodes,
        List<DiagramEdge> edges,
        List<String> labels) {
}
