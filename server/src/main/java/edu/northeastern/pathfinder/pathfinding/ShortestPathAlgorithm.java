package edu.northeastern.pathfinder.pathfinding;

import edu.northeastern.pathfinder.graph.Graph;

/**
 * Minimal common contract for weighted shortest-path algorithms.
 */
public interface ShortestPathAlgorithm {
    PathfindingResult findPath(Graph graph, String startNodeId, String endNodeId);
}
