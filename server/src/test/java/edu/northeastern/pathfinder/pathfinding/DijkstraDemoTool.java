package edu.northeastern.pathfinder.pathfinding;

import edu.northeastern.pathfinder.graph.Edge;
import edu.northeastern.pathfinder.graph.GeoJsonGraphBuilder;
import edu.northeastern.pathfinder.graph.Graph;

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
 * Lightweight demo utility for running Dijkstra on the current GeoJSON graph.
 *
 * Usage:
 *   java ... DijkstraDemoTool [path-to-geojson]
 *
 * If no argument is provided, defaults to ../data/full.geojson when run from server directory.
 */
public final class DijkstraDemoTool {
    private static final int PREVIEW_COUNT = 5;

    private DijkstraDemoTool() {
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
            System.out.println("Not enough nodes in graph to run demo pairs. nodeCount=" + nodeIds.size());
            return;
        }

        DijkstraShortestPath dijkstra = new DijkstraShortestPath();
        List<NodePair> pairs = buildDeterministicReachablePairs(graph, nodeIds);

        if (pairs.isEmpty()) {
            System.out.println("Could not derive deterministic reachable demo pairs from current graph.");
            return;
        }

        System.out.println("=== Dijkstra Demo ===");
        System.out.println("Source: " + geoJsonPath.toAbsolutePath());
        System.out.println("Node count: " + graph.getNodeCount());
        System.out.println("Edge count: " + graph.getEdgeCount());
        System.out.println();

        for (int i = 0; i < pairs.size(); i++) {
            NodePair pair = pairs.get(i);
            PathfindingResult result = dijkstra.findPath(graph, pair.startNodeId(), pair.endNodeId());

            System.out.println("Case " + (i + 1));
            System.out.println("  start: " + pair.startNodeId());
            System.out.println("  end:   " + pair.endNodeId());
            System.out.println("  pathFound: " + result.isPathFound());

            if (result.isPathFound()) {
                List<String> path = result.getPathNodeIds();
                System.out.println("  totalDistanceMeters: " + String.format("%.2f", result.getTotalDistanceMeters()));
                System.out.println("  pathNodeCount: " + path.size());
                System.out.println("  firstNodes: " + previewHead(path, PREVIEW_COUNT));
                System.out.println("  lastNodes:  " + previewTail(path, PREVIEW_COUNT));
            } else {
                System.out.println("  totalDistanceMeters: INF");
                System.out.println("  pathNodeCount: 0");
                System.out.println("  firstNodes: []");
                System.out.println("  lastNodes:  []");
            }
            System.out.println();
        }
    }

    /**
     * Deterministic and demo-friendly pair selection:
     * 1) choose the first lexicographic node that has outgoing edges as seed
     * 2) BFS from seed to gather reachable nodes in deterministic order
     * 3) pick end nodes from fixed percentile positions in that reachable list
     *
     * This keeps sample pairs stable and strongly increases chance of pathFound=true.
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
        // remove seed itself so every case is a real path request
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

    private static List<String> previewHead(List<String> items, int n) {
        return items.subList(0, Math.min(n, items.size()));
    }

    private static List<String> previewTail(List<String> items, int n) {
        int size = items.size();
        int from = Math.max(0, size - n);
        return items.subList(from, size);
    }

    private record NodePair(String startNodeId, String endNodeId) {
    }
}
