package edu.northeastern.pathfinder.pathfinding;

import edu.northeastern.pathfinder.graph.Edge;
import edu.northeastern.pathfinder.graph.Graph;
import edu.northeastern.pathfinder.graph.ReverseEdge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Bidirectional Dijkstra that alternates forward and backward frontiers
 * and terminates when the sum of the two frontier tops exceeds the best
 * known meeting cost. Roughly halves the explored-node count vs. one-sided
 * Dijkstra on road networks.
 *
 * Correctness requires non-negative, symmetric edge costs — the cost
 * reported for an edge in the backward frontier must equal its forward
 * cost, which holds for all current {@link PathCostModel} implementations
 * (distance, time, balanced, safe-walk all key only on edge attributes).
 */
public final class BidirectionalDijkstra implements ShortestPathAlgorithm {

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

        Map<String, Double> distF = new HashMap<>();
        Map<String, Double> distB = new HashMap<>();
        Map<String, String> prevF = new HashMap<>();
        Map<String, String> prevB = new HashMap<>();
        Set<String> settledF = new HashSet<>();
        Set<String> settledB = new HashSet<>();

        PriorityQueue<State> openF = new PriorityQueue<>((a, b) -> Double.compare(a.distance, b.distance));
        PriorityQueue<State> openB = new PriorityQueue<>((a, b) -> Double.compare(a.distance, b.distance));

        distF.put(startNodeId, 0.0);
        distB.put(endNodeId, 0.0);
        openF.add(new State(startNodeId, 0.0));
        openB.add(new State(endNodeId, 0.0));

        double bestCost = Double.POSITIVE_INFINITY;
        String meetingNode = null;

        while (!openF.isEmpty() && !openB.isEmpty()) {
            double topF = openF.peek().distance;
            double topB = openB.peek().distance;

            // Standard bidirectional-Dijkstra termination: the best known
            // s-t path cannot be improved once both frontiers have grown
            // past the meeting point.
            if (topF + topB >= bestCost) {
                break;
            }

            // Alternate sides: expand the smaller frontier first to keep
            // settled sets balanced.
            if (topF <= topB) {
                State cur = openF.poll();
                if (!settledF.add(cur.nodeId)) continue;
                double curG = distF.getOrDefault(cur.nodeId, Double.POSITIVE_INFINITY);
                if (cur.distance > curG) continue;

                for (Edge edge : graph.getOutgoing(cur.nodeId)) {
                    double edgeCost = costModel.edgeCost(edge);
                    if (!Double.isFinite(edgeCost) || edgeCost < 0) continue;

                    String next = edge.getToNodeId();
                    if (settledF.contains(next)) continue;

                    double cand = curG + edgeCost;
                    if (cand < distF.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                        distF.put(next, cand);
                        prevF.put(next, cur.nodeId);
                        openF.add(new State(next, cand));

                        Double bDist = distB.get(next);
                        if (bDist != null) {
                            double total = cand + bDist;
                            if (total < bestCost) {
                                bestCost = total;
                                meetingNode = next;
                            }
                        }
                    }
                }
            } else {
                State cur = openB.poll();
                if (!settledB.add(cur.nodeId)) continue;
                double curG = distB.getOrDefault(cur.nodeId, Double.POSITIVE_INFINITY);
                if (cur.distance > curG) continue;

                for (ReverseEdge rev : graph.getIncoming(cur.nodeId)) {
                    double edgeCost = costModel.edgeCost(rev.getOriginalEdge());
                    if (!Double.isFinite(edgeCost) || edgeCost < 0) continue;

                    String next = rev.getFromNodeId();
                    if (settledB.contains(next)) continue;

                    double cand = curG + edgeCost;
                    if (cand < distB.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                        distB.put(next, cand);
                        prevB.put(next, cur.nodeId);
                        openB.add(new State(next, cand));

                        Double fDist = distF.get(next);
                        if (fDist != null) {
                            double total = cand + fDist;
                            if (total < bestCost) {
                                bestCost = total;
                                meetingNode = next;
                            }
                        }
                    }
                }
            }
        }

        if (meetingNode == null || !Double.isFinite(bestCost)) {
            return PathfindingResult.notFound();
        }

        List<String> path = reconstructPath(meetingNode, startNodeId, endNodeId, prevF, prevB);
        if (path.isEmpty()) {
            return PathfindingResult.notFound();
        }
        return PathfindingResult.found(bestCost, path);
    }

    private List<String> reconstructPath(
            String meeting,
            String startNodeId,
            String endNodeId,
            Map<String, String> prevF,
            Map<String, String> prevB
    ) {
        // Forward half: start -> meeting
        List<String> forward = new ArrayList<>();
        String cur = meeting;
        forward.add(cur);
        while (!cur.equals(startNodeId)) {
            String parent = prevF.get(cur);
            if (parent == null) return List.of();
            forward.add(parent);
            cur = parent;
        }
        Collections.reverse(forward);

        // Backward half: meeting -> end
        List<String> backward = new ArrayList<>();
        cur = meeting;
        while (!cur.equals(endNodeId)) {
            String parent = prevB.get(cur);
            if (parent == null) return List.of();
            backward.add(parent);
            cur = parent;
        }

        List<String> full = new ArrayList<>(forward.size() + backward.size());
        full.addAll(forward);
        full.addAll(backward);
        return full;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private record State(String nodeId, double distance) {
    }
}
