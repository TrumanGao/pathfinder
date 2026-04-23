package edu.northeastern.pathfinder.pathfinding;

import edu.northeastern.pathfinder.graph.Edge;
import edu.northeastern.pathfinder.graph.Node;

/**
 * Request-scoped edge-cost and heuristic policy.
 * Lets routing pick distance / time / balanced costs per request
 * without mutating shared graph weights.
 */
public interface PathCostModel {
    double edgeCost(Edge edge);

    double heuristicCost(Node from, Node to);

    static PathCostModel distanceOnly() {
        return new PathCostModel() {
            @Override
            public double edgeCost(Edge edge) {
                return edge.getSegmentDistanceMeters();
            }

            @Override
            public double heuristicCost(Node from, Node to) {
                return haversineMeters(from, to);
            }
        };
    }

    static double haversineMeters(Node from, Node to) {
        double r = 6_371_000.0;
        double dLat = Math.toRadians(to.getLat() - from.getLat());
        double dLon = Math.toRadians(to.getLon() - from.getLon());
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(from.getLat())) * Math.cos(Math.toRadians(to.getLat()))
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        return r * (2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a)));
    }
}
