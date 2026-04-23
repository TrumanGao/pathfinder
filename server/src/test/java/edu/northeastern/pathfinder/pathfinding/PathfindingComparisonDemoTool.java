package edu.northeastern.pathfinder.pathfinding;

import edu.northeastern.pathfinder.graph.Edge;
import edu.northeastern.pathfinder.graph.GeoJsonGraphBuilder;
import edu.northeastern.pathfinder.graph.Graph;
import edu.northeastern.pathfinder.graph.Node;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lightweight demo utility to compare Dijkstra and A* on deterministic reachable node pairs.
 */
public final class PathfindingComparisonDemoTool {
    private static final double DIST_TOLERANCE_METERS = 1e-6;

    private PathfindingComparisonDemoTool() {
    }

    public static void main(String[] args) throws IOException {
        Path geoJsonPath = args.length > 0
                ? Paths.get(args[0])
                : Paths.get("..", "data", "full.geojson");

        GeoJsonGraphBuilder.BuildResult buildResult = new GeoJsonGraphBuilder().build(geoJsonPath);
        Graph graph = buildResult.getGraph();

        List<String> nodeIds = new ArrayList<>(graph.getNodesById().keySet());
        nodeIds.sort(Comparator.naturalOrder());

        if (nodeIds.size() < 10) {
            System.out.println("Not enough nodes in graph to run comparison demo. nodeCount=" + nodeIds.size());
            return;
        }

        List<NodePair> pairs = buildDeterministicReachablePairs(graph, nodeIds);
        if (pairs.isEmpty()) {
            System.out.println("Could not derive deterministic reachable demo pairs from current graph.");
            return;
        }

        ShortestPathAlgorithm dijkstra = new DijkstraShortestPath();
        ShortestPathAlgorithm astar = new AStarShortestPath();

        System.out.println("=== Dijkstra vs A* Demo Comparison ===");
        System.out.println("Source: " + geoJsonPath.toAbsolutePath());
        System.out.println("Node count: " + graph.getNodeCount());
        System.out.println("Edge count: " + graph.getEdgeCount());
        System.out.println("Distance tolerance: " + DIST_TOLERANCE_METERS + " m");
        System.out.println();

        for (int i = 0; i < pairs.size(); i++) {
            NodePair pair = pairs.get(i);

            TimedResult d = runTimed(dijkstra, graph, pair.startNodeId(), pair.endNodeId());
            TimedResult a = runTimed(astar, graph, pair.startNodeId(), pair.endNodeId());

            boolean sameFound = d.result().isPathFound() == a.result().isPathFound();
            boolean sameDistance = sameFound
                    && (!d.result().isPathFound()
                    || Math.abs(d.result().getTotalDistanceMeters() - a.result().getTotalDistanceMeters()) <= DIST_TOLERANCE_METERS);
            boolean samePathCount = sameFound
                    && d.result().getPathNodeIds().size() == a.result().getPathNodeIds().size();
            boolean consistent = sameFound && sameDistance && samePathCount;

            double heuristicMeters = heuristicEstimateMeters(graph, pair.startNodeId(), pair.endNodeId());
            double actualMeters = d.result().isPathFound()
                    ? d.result().getTotalDistanceMeters()
                    : (a.result().isPathFound() ? a.result().getTotalDistanceMeters() : Double.NaN);

            System.out.println("Case " + (i + 1));
            System.out.println("  start: " + pair.startNodeId());
            System.out.println("  end:   " + pair.endNodeId());

            printAlgoLine("Dijkstra", d);
            printAlgoLine("A*", a);

            if (Double.isFinite(actualMeters) && actualMeters > 0) {
                double ratio = heuristicMeters / actualMeters;
                System.out.println("  heuristicEstimateMeters: " + String.format("%.2f", heuristicMeters));
                System.out.println("  heuristic/actual ratio: " + String.format("%.6f", ratio));
            } else {
                System.out.println("  heuristicEstimateMeters: " + String.format("%.2f", heuristicMeters));
                System.out.println("  heuristic/actual ratio: N/A");
            }

            System.out.println("  Consistency:");
            System.out.println("    samePathFound: " + sameFound);
            System.out.println("    sameDistanceWithinTolerance: " + sameDistance);
            System.out.println("    samePathNodeCount: " + samePathCount);
            System.out.println("    overallConsistent: " + consistent);
            System.out.println();
        }
    }

    private static TimedResult runTimed(
            ShortestPathAlgorithm algorithm,
            Graph graph,
            String startNodeId,
            String endNodeId
    ) {
        long t0 = System.nanoTime();
        PathfindingResult result = algorithm.findPath(graph, startNodeId, endNodeId);
        long t1 = System.nanoTime();
        double elapsedMs = (t1 - t0) / 1_000_000.0;
        return new TimedResult(result, elapsedMs);
    }

    private static void printAlgoLine(String name, TimedResult tr) {
        PathfindingResult r = tr.result();
        String distance = r.isPathFound()
                ? String.format("%.2f", r.getTotalDistanceMeters())
                : "INF";

        System.out.println("  " + name + ": "
                + "pathFound=" + r.isPathFound()
                + ", totalDistanceMeters=" + distance
                + ", pathNodeCount=" + r.getPathNodeIds().size()
                + ", runtimeMs=" + String.format("%.3f", tr.elapsedMs()));
    }

    private static double heuristicEstimateMeters(Graph graph, String startNodeId, String endNodeId) {
        var start = graph.getNode(startNodeId);
        var end = graph.getNode(endNodeId);
        if (start.isEmpty() || end.isEmpty()) {
            return Double.NaN;
        }
        return haversineMeters(start.get(), end.get());
    }

    private static double haversineMeters(Node from, Node to) {
        double r = 6_371_000.0;
        double dLat = Math.toRadians(to.getLat() - from.getLat());
        double dLon = Math.toRadians(to.getLon() - from.getLon());
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(from.getLat())) * Math.cos(Math.toRadians(to.getLat()))
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        return r * (2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a)));
    }

    /**
     * Same deterministic pair-selection approach as DijkstraDemoTool:
     * 1) choose first lexicographic node with outgoing edges as seed
     * 2) BFS reachable set with sorted outgoing order
     * 3) choose endpoints from fixed percentile indices
     */
    private static List<NodePair> buildDeterministicReachablePairs(Graph graph, List<String> sortedNodeIds) {
        String seed = null;
        for (String nodeId : sortedNodeIds) {
            if (!graph.getOutgoing(nodeId).isEmpty()) {
                seed = nodeId;
                break;
            }
        }
        if (seed == null) {
            return List.of();
        }

        List<String> reachable = collectReachableFromSeed(graph, seed);
        reachable.remove(seed);
        if (reachable.isEmpty()) {
            return List.of();
        }

        List<Integer> targetIndices = List.of(
                percentileIndex(reachable.size(), 0.15),
                percentileIndex(reachable.size(), 0.45),
                percentileIndex(reachable.size(), 0.75)
        );

        Set<String> chosenEnds = new LinkedHashSet<>();
        for (int idx : targetIndices) {
            chosenEnds.add(reachable.get(idx));
        }

        List<NodePair> pairs = new ArrayList<>();
        for (String end : chosenEnds) {
            if (!seed.equals(end)) {
                pairs.add(new NodePair(seed, end));
            }
        }
        return pairs;
    }

    private static List<String> collectReachableFromSeed(Graph graph, String seed) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();

        visited.add(seed);
        queue.add(seed);

        while (!queue.isEmpty()) {
            String cur = queue.poll();

            List<Edge> outgoing = new ArrayList<>(graph.getOutgoing(cur));
            outgoing.sort(Comparator.comparing(Edge::getToNodeId));

            for (Edge edge : outgoing) {
                String next = edge.getToNodeId();
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }

        return new ArrayList<>(visited);
    }

    private static int percentileIndex(int size, double percentile) {
        if (size <= 1) {
            return 0;
        }
        int idx = (int) Math.floor((size - 1) * percentile);
        return Math.max(0, Math.min(idx, size - 1));
    }

    private record NodePair(String startNodeId, String endNodeId) {
    }

    private record TimedResult(PathfindingResult result, double elapsedMs) {
    }
}
