package edu.northeastern.pathfinder.pathfinding;

import edu.northeastern.pathfinder.graph.Graph;

/**
 * Common contract for shortest-path algorithms.
 * Implementations accept a request-scoped cost model.
 */
public interface ShortestPathAlgorithm {
    default PathfindingResult findPath(Graph graph, String startNodeId, String endNodeId) {
        return findPath(graph, startNodeId, endNodeId, PathCostModel.distanceOnly());
    }

    PathfindingResult findPath(Graph graph, String startNodeId, String endNodeId, PathCostModel costModel);
}
