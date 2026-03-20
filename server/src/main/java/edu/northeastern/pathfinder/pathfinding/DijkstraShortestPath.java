package edu.northeastern.pathfinder.pathfinding;

import edu.northeastern.pathfinder.graph.Edge;
import edu.northeastern.pathfinder.graph.Graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Dijkstra shortest path using segmentDistanceMeters as edge weight.
 */
public final class DijkstraShortestPath implements ShortestPathAlgorithm {

    @Override
    public PathfindingResult findPath(Graph graph, String startNodeId, String endNodeId, PathCostModel costModel) {
        Objects.requireNonNull(graph, "graph must not be null");
        Objects.requireNonNull(costModel, "costModel must not be null");
        if (isBlank(startNodeId) || isBlank(endNodeId)) {
            return PathfindingResult.notFound();
        }
        if (graph.getNode(startNodeId).isEmpty() || graph.getNode(endNodeId).isEmpty()) {
            return PathfindingResult.notFound();
        }
        if (startNodeId.equals(endNodeId)) {
            return PathfindingResult.found(0.0, List.of(startNodeId));
        }

        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        PriorityQueue<State> pq = new PriorityQueue<>((a, b) -> Double.compare(a.distance, b.distance));

        dist.put(startNodeId, 0.0);
        pq.add(new State(startNodeId, 0.0));

        while (!pq.isEmpty()) {
            State cur = pq.poll();
            double bestKnown = dist.getOrDefault(cur.nodeId, Double.POSITIVE_INFINITY);
            if (cur.distance > bestKnown) {
                continue;
            }

            if (cur.nodeId.equals(endNodeId)) {
                List<String> path = reconstructPath(prev, startNodeId, endNodeId);
                if (path.isEmpty()) {
                    return PathfindingResult.notFound();
                }
                return PathfindingResult.found(cur.distance, path);
            }

            for (Edge edge : graph.getOutgoing(cur.nodeId)) {
                double edgeCost = costModel.edgeCost(edge);
                if (!Double.isFinite(edgeCost) || edgeCost < 0) {
                    continue;
                }

                String next = edge.getToNodeId();
                double cand = cur.distance + edgeCost;
                double nextBest = dist.getOrDefault(next, Double.POSITIVE_INFINITY);

                if (cand < nextBest) {
                    dist.put(next, cand);
                    prev.put(next, cur.nodeId);
                    pq.add(new State(next, cand));
                }
            }
        }

        return PathfindingResult.notFound();
    }

    private List<String> reconstructPath(Map<String, String> prev, String startNodeId, String endNodeId) {
        List<String> path = new ArrayList<>();
        String cur = endNodeId;
        path.add(cur);

        while (!cur.equals(startNodeId)) {
            cur = prev.get(cur);
            if (cur == null) {
                return List.of();
            }
            path.add(cur);
        }

        Collections.reverse(path);
        return path;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private record State(String nodeId, double distance) {
    }
}
