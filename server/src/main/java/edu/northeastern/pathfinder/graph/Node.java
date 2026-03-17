package edu.northeastern.pathfinder.graph;

import java.util.Objects;

/**
 * Routable graph node identified by a stable coordinate-based node id.
 */
public final class Node {
    private final String nodeId;
    private final double lon;
    private final double lat;

    public Node(String nodeId, double lon, double lat) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.lon = lon;
        this.lat = lat;
    }

    public String getNodeId() {
        return nodeId;
    }

    public double getLon() {
        return lon;
    }

    public double getLat() {
        return lat;
    }
}
