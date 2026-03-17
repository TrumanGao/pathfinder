package edu.northeastern.pathfinder.pathfinding;

import edu.northeastern.pathfinder.graph.Edge;
import edu.northeastern.pathfinder.graph.Graph;
import edu.northeastern.pathfinder.graph.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Minimal A* shortest path using segmentDistanceMeters as g-cost.
 */
public final class AStarShortestPath implements ShortestPathAlgorithm {

    @Override
    public PathfindingResult findPath(Graph graph, String startNodeId, String endNodeId) {
        Objects.requireNonNull(graph, "graph must not be null");
        if (isBlank(startNodeId) || isBlank(endNodeId)) {
            return PathfindingResult.notFound();
        }

        var startOpt = graph.getNode(startNodeId);
        var endOpt = graph.getNode(endNodeId);
        if (startOpt.isEmpty() || endOpt.isEmpty()) {
            return PathfindingResult.notFound();
        }
        if (startNodeId.equals(endNodeId)) {
            return PathfindingResult.found(0.0, List.of(startNodeId));
        }

        Map<String, Double> gScore = new HashMap<>();
        Map<String, String> prev = new HashMap<>();

        PriorityQueue<State> open = new PriorityQueue<>(
                (a, b) -> {
                    int cmp = Double.compare(a.fScore, b.fScore);
                    if (cmp != 0) {
                        return cmp;
                    }
                    return Double.compare(a.gScore, b.gScore);
                }
        );

        double startHeuristic = heuristicMeters(startOpt.get(), endOpt.get());
        gScore.put(startNodeId, 0.0);
        open.add(new State(startNodeId, 0.0, startHeuristic));

        while (!open.isEmpty()) {
            State cur = open.poll();
            double bestKnownG = gScore.getOrDefault(cur.nodeId, Double.POSITIVE_INFINITY);
            if (cur.gScore > bestKnownG) {
                continue;
            }

            if (cur.nodeId.equals(endNodeId)) {
                List<String> path = reconstructPath(prev, startNodeId, endNodeId);
                if (path.isEmpty()) {
                    return PathfindingResult.notFound();
                }
                return PathfindingResult.found(cur.gScore, path);
            }

            for (Edge edge : graph.getOutgoing(cur.nodeId)) {
                double w = edge.getSegmentDistanceMeters();
                if (w < 0) {
                    continue;
                }

                String next = edge.getToNodeId();
                var nextOpt = graph.getNode(next);
                if (nextOpt.isEmpty()) {
                    continue;
                }

                double tentativeG = cur.gScore + w;
                double knownG = gScore.getOrDefault(next, Double.POSITIVE_INFINITY);
                if (tentativeG < knownG) {
                    gScore.put(next, tentativeG);
                    prev.put(next, cur.nodeId);

                    double h = heuristicMeters(nextOpt.get(), endOpt.get());
                    open.add(new State(next, tentativeG, tentativeG + h));
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

    /**
     * Admissible heuristic for this stage: straight-line geographic distance.
     */
    private double heuristicMeters(Node from, Node to) {
        return haversineMeters(from.getLat(), from.getLon(), to.getLat(), to.getLon());
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        return r * (2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a)));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private record State(String nodeId, double gScore, double fScore) {
    }
}
