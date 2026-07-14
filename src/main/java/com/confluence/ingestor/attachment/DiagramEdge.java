package com.confluence.ingestor.attachment;

public record DiagramEdge(
        String sourceId,
        String targetId,
        String label,
        String parentId,
        int order,
        String relationshipType,
        String sourceMultiplicity,
        String targetMultiplicity) {

    public DiagramEdge(String sourceId, String targetId, String label, String parentId, int order) {
        this(sourceId, targetId, label, parentId, order, null, null, null);
    }
}
