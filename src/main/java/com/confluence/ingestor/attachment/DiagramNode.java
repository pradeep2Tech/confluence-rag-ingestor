package com.confluence.ingestor.attachment;

import java.util.List;

public record DiagramNode(
        String id,
        String label,
        String type,
        String parentId,
        int order,
        List<String> attributes,
        List<String> methods,
        List<String> stereotypes) {

    public DiagramNode(String id, String label, String type, String parentId, int order) {
        this(id, label, type, parentId, order, List.of(), List.of(), List.of());
    }
}
