package edu.northeastern.pathfinder.graph;

/**
 * View of a forward {@link Edge} used when traversing the graph from a
 * destination back toward a source, as in the backward half of a
 * bidirectional search. {@code fromNodeId} is the original edge's source.
 */
public final class ReverseEdge {
    private final String fromNodeId;
    private final Edge originalEdge;

    public ReverseEdge(String fromNodeId, Edge originalEdge) {
        this.fromNodeId = fromNodeId;
        this.originalEdge = originalEdge;
    }

    public String getFromNodeId() {
        return fromNodeId;
    }

    public Edge getOriginalEdge() {
        return originalEdge;
    }
}
