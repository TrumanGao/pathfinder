package edu.northeastern.pathfinder.pathfinding;

import edu.northeastern.pathfinder.graph.Edge;
import edu.northeastern.pathfinder.graph.Node;

/**
 * EN: Request-scoped edge-cost and heuristic policy for pathfinding.
 * This layer lets routing choose distance, time, or balanced costs per request without changing
 * graph storage. It intentionally stays small and does not try to be a full routing framework.
 * 中文：用于寻路的“单次请求范围内”边成本与启发式策略。
 * 它让路由层可以按请求选择 distance、time 或 balanced 成本，而无需修改图存储。
 * 该层刻意保持轻量，不尝试演变成完整的路由框架。
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
