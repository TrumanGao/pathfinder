package edu.northeastern.pathfinder.graph;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Directed edge from an adjacency-list source node to a destination node.
 *
 * Source node id is represented by the adjacency-list key in Graph.
 * Missing source values are preserved as null; no defaults are assigned.
 */
public final class Edge {
    private final String toNodeId;
    private final double segmentDistanceMeters;

    private final String sourceFeatureId;
    private final String highway;
    private final String maxspeedRaw;
    private final String onewayRaw;
    private final String roadName;

    private final Map<String, Object> rawTags;

    public Edge(
            String toNodeId,
            double segmentDistanceMeters,
            String sourceFeatureId,
            String highway,
            String maxspeedRaw,
            String onewayRaw,
            String roadName,
            Map<String, Object> rawTags
    ) {
        this.toNodeId = Objects.requireNonNull(toNodeId, "toNodeId must not be null");
        this.segmentDistanceMeters = segmentDistanceMeters;
        this.sourceFeatureId = sourceFeatureId;
        this.highway = highway;
        this.maxspeedRaw = maxspeedRaw;
        this.onewayRaw = onewayRaw;
        this.roadName = roadName;

        Map<String, Object> copy = (rawTags == null) ? new HashMap<>() : new HashMap<>(rawTags);
        this.rawTags = Collections.unmodifiableMap(copy);
    }

    public String getToNodeId() {
        return toNodeId;
    }

    public double getSegmentDistanceMeters() {
        return segmentDistanceMeters;
    }

    public String getSourceFeatureId() {
        return sourceFeatureId;
    }

    public String getHighway() {
        return highway;
    }

    public String getMaxspeedRaw() {
        return maxspeedRaw;
    }

    public String getOnewayRaw() {
        return onewayRaw;
    }

    public String getRoadName() {
        return roadName;
    }

    /**
     * Full raw properties copied from source feature.
     */
    public Map<String, Object> getRawTags() {
        return rawTags;
    }
}
