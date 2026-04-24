package edu.northeastern.pathfinder.benchmark;

import edu.northeastern.pathfinder.graph.Edge;
import edu.northeastern.pathfinder.graph.GeoJsonLoader;
import edu.northeastern.pathfinder.graph.Graph;
import edu.northeastern.pathfinder.graph.Node;
import edu.northeastern.pathfinder.graph.NodeKdTree;
import edu.northeastern.pathfinder.pathfinding.AStarShortestPath;
import edu.northeastern.pathfinder.pathfinding.BidirectionalDijkstra;
import edu.northeastern.pathfinder.pathfinding.DijkstraShortestPath;
import edu.northeastern.pathfinder.pathfinding.PathCostModel;
import edu.northeastern.pathfinder.pathfinding.PathfindingResult;
import edu.northeastern.pathfinder.pathfinding.ShortestPathAlgorithm;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stand-alone harness that compares the three routing algorithms and the
 * kd-tree-backed nearest-node lookup on the real GeoJSON dataset.
 *
 * Emits one CSV row per (pair, algorithm) with:
 *   pair_id, start_node, end_node, algorithm, path_found, distance_m,
 *   edge_relaxations, wall_time_ms, path_nodes
 *
 * Plus summary rows for the kd-tree vs linear-scan nearest query.
 *
 * <p>Intentionally placed in {@code src/test/java} so the production jar
 * stays clean; this class is never loaded at runtime.
 *
 * <h3>Run it</h3>
 * <pre>
 *   cd server
 *   ./mvnw.cmd test-compile exec:java \
 *       -Dexec.classpathScope=test \
 *       -Dexec.mainClass=edu.northeastern.pathfinder.benchmark.BenchmarkTool \
 *       -Dexec.args="--pairs 200 --seed 42 --out ../analysis/results/benchmark-latest.csv"
 * </pre>
 */
public final class BenchmarkTool {

    private BenchmarkTool() {}

    public static void main(String[] args) throws IOException {
        Config config = Config.parse(args);

        System.out.println("Loading graph from " + config.geoJsonPath.toAbsolutePath());
        long t0 = System.nanoTime();
        GeoJsonLoader loader = new GeoJsonLoader(
                config.geoJsonPath.toString(),
                config.cachePath.toString(),
                false
        );
        Graph graph = loader.getGraph();
        long loadMs = (System.nanoTime() - t0) / 1_000_000L;
        System.out.printf("Graph ready in %d ms: %d nodes, %d edges%n",
                loadMs, graph.getNodeCount(), graph.getEdgeCount());

        if (graph.getNodeCount() == 0) {
            System.err.println("Graph is empty; nothing to benchmark.");
            return;
        }

        // Warm up the reverse adjacency index so the first bidirectional
        // query does not pay the one-time build cost inside its timer.
        graph.getIncoming(graph.getNodesById().keySet().iterator().next());

        NodeKdTree kdTree = new NodeKdTree(graph.getNodesById().values());

        ShortestPathAlgorithm dijkstra = new DijkstraShortestPath();
        ShortestPathAlgorithm astar = new AStarShortestPath();
        ShortestPathAlgorithm bidi = new BidirectionalDijkstra();

        List<NodePair> pairs = selectReachablePairs(graph, config.pairCount, config.seed);
        System.out.printf("Selected %d reachable (start, end) pairs (seed=%d)%n", pairs.size(), config.seed);

        try (PrintStream out = openOutput(config.outPath)) {
            writeRoutingHeader(out);

            PerAlgorithmAggregate aggDijk = new PerAlgorithmAggregate("dijkstra");
            PerAlgorithmAggregate aggAstar = new PerAlgorithmAggregate("astar");
            PerAlgorithmAggregate aggBidi = new PerAlgorithmAggregate("bidirectional");

            int pairId = 0;
            for (NodePair pair : pairs) {
                pairId++;
                runOne(pairId, pair, "dijkstra", dijkstra, graph, out, aggDijk);
                runOne(pairId, pair, "astar", astar, graph, out, aggAstar);
                runOne(pairId, pair, "bidirectional", bidi, graph, out, aggBidi);
            }

            out.println();
            writeSummary(out, List.of(aggDijk, aggAstar, aggBidi));
            writeNearestBenchmark(out, graph, kdTree, config);
        }

        System.out.println("Wrote benchmark CSV to " + (config.outPath == null ? "stdout" : config.outPath.toAbsolutePath()));
    }

    // ============================================================
    // Routing benchmark
    // ============================================================

    private static void runOne(
            int pairId,
            NodePair pair,
            String algorithmName,
            ShortestPathAlgorithm algorithm,
            Graph graph,
            PrintStream out,
            PerAlgorithmAggregate agg
    ) {
        CountingCostModel counter = new CountingCostModel(PathCostModel.distanceOnly());

        long t0 = System.nanoTime();
        PathfindingResult result = algorithm.findPath(graph, pair.startId, pair.endId, counter);
        long elapsedNs = System.nanoTime() - t0;
        double elapsedMs = elapsedNs / 1_000_000.0;

        out.printf("%d,%s,%s,%s,%s,%.2f,%d,%.3f,%d%n",
                pairId,
                pair.startId,
                pair.endId,
                algorithmName,
                result.isPathFound(),
                result.isPathFound() ? result.getTotalDistanceMeters() : -1.0,
                counter.edgeCostCalls.get(),
                elapsedMs,
                result.isPathFound() ? result.getPathNodeIds().size() : 0
        );

        agg.record(result, counter.edgeCostCalls.get(), elapsedMs);
    }

    private static void writeRoutingHeader(PrintStream out) {
        out.println("pair_id,start_node,end_node,algorithm,path_found,distance_m,edge_relaxations,wall_time_ms,path_nodes");
    }

    private static void writeSummary(PrintStream out, List<PerAlgorithmAggregate> aggregates) {
        out.println("# Summary (algorithm, pairs, avg_edge_relaxations, avg_time_ms, median_time_ms, correctness_vs_dijkstra)");
        PerAlgorithmAggregate reference = null;
        for (PerAlgorithmAggregate a : aggregates) {
            if ("dijkstra".equals(a.name)) {
                reference = a;
                break;
            }
        }
        for (PerAlgorithmAggregate a : aggregates) {
            String correctness = reference == null ? "n/a" : a.correctnessVs(reference);
            out.printf("# %s,%d,%.0f,%.3f,%.3f,%s%n",
                    a.name,
                    a.count,
                    a.avgRelaxations(),
                    a.avgTimeMs(),
                    a.medianTimeMs(),
                    correctness);
        }
    }

    // ============================================================
    // Nearest-neighbour benchmark: kd-tree vs linear scan
    // ============================================================

    private static void writeNearestBenchmark(PrintStream out, Graph graph, NodeKdTree kdTree, Config config) {
        int queries = Math.max(50, config.pairCount / 2);
        Random rng = new Random(config.seed + 1);

        List<double[]> samplePoints = new ArrayList<>(queries);
        List<Node> nodes = new ArrayList<>(graph.getNodesById().values());
        for (int i = 0; i < queries; i++) {
            Node n = nodes.get(rng.nextInt(nodes.size()));
            // Query off-node by ~100 m to force real nearest-neighbour work.
            double latJitter = (rng.nextDouble() - 0.5) * 0.002;
            double lonJitter = (rng.nextDouble() - 0.5) * 0.002;
            samplePoints.add(new double[]{ n.getLat() + latJitter, n.getLon() + lonJitter });
        }

        // kd-tree
        long t0 = System.nanoTime();
        int kdOk = 0;
        for (double[] p : samplePoints) {
            NodeKdTree.NearestMatch m = kdTree.nearest(p[0], p[1]);
            if (m != null) kdOk++;
        }
        double kdAvgMicros = (System.nanoTime() - t0) / 1000.0 / queries;

        // Linear scan
        long t1 = System.nanoTime();
        int linOk = 0;
        for (double[] p : samplePoints) {
            String winner = linearScanNearest(graph, p[0], p[1]);
            if (winner != null) linOk++;
        }
        double linAvgMicros = (System.nanoTime() - t1) / 1000.0 / queries;

        out.println();
        out.println("# Nearest-neighbour benchmark (avg microseconds per query)");
        out.printf("# method,queries,avg_us_per_query,speedup_vs_linear%n");
        out.printf("# linear_scan,%d,%.1f,1.00x%n", queries, linAvgMicros);
        out.printf("# kd_tree,%d,%.1f,%.2fx%n", queries, kdAvgMicros, linAvgMicros / Math.max(0.001, kdAvgMicros));
        // Sanity line so we notice silent correctness drift:
        if (kdOk != linOk) {
            out.printf("# WARNING: kd-tree matched %d / %d, linear scan matched %d / %d%n", kdOk, queries, linOk, queries);
        }
    }

    private static String linearScanNearest(Graph graph, double lat, double lon) {
        String bestId = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (Node n : graph.getNodesById().values()) {
            double d = haversine(lat, lon, n.getLat(), n.getLon());
            if (d < bestD) {
                bestD = d;
                bestId = n.getNodeId();
            }
        }
        return bestId;
    }

    // ============================================================
    // Pair selection
    // ============================================================

    /**
     * Select {@code count} deterministic node pairs that are likely to be
     * reachable (both endpoints have outgoing edges). Does not guarantee
     * reachability — that is what the benchmark itself tests.
     */
    private static List<NodePair> selectReachablePairs(Graph graph, int count, long seed) {
        List<String> candidateIds = new ArrayList<>();
        for (var entry : graph.getOutgoingAdj().entrySet()) {
            if (!entry.getValue().isEmpty()) {
                candidateIds.add(entry.getKey());
            }
        }
        if (candidateIds.size() < 2) {
            throw new IllegalStateException("Too few nodes with outgoing edges for benchmark");
        }
        Random rng = new Random(seed);
        List<NodePair> pairs = new ArrayList<>(count);
        while (pairs.size() < count) {
            String a = candidateIds.get(rng.nextInt(candidateIds.size()));
            String b = candidateIds.get(rng.nextInt(candidateIds.size()));
            if (!a.equals(b)) {
                pairs.add(new NodePair(a, b));
            }
        }
        return pairs;
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static PrintStream openOutput(Path outPath) throws IOException {
        if (outPath == null) {
            return System.out;
        }
        if (outPath.getParent() != null) {
            Files.createDirectories(outPath.getParent());
        }
        return new PrintStream(Files.newOutputStream(outPath,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        return r * (2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a)));
    }

    private record NodePair(String startId, String endId) {}

    /** Wraps a PathCostModel and counts every edgeCost / heuristicCost call. */
    private static final class CountingCostModel implements PathCostModel {
        private final PathCostModel inner;
        private final AtomicLong edgeCostCalls = new AtomicLong();
        private final AtomicLong heuristicCalls = new AtomicLong();

        CountingCostModel(PathCostModel inner) {
            this.inner = inner;
        }

        @Override
        public double edgeCost(Edge edge) {
            edgeCostCalls.incrementAndGet();
            return inner.edgeCost(edge);
        }

        @Override
        public double heuristicCost(Node from, Node to) {
            heuristicCalls.incrementAndGet();
            return inner.heuristicCost(from, to);
        }
    }

    /** Running aggregate per algorithm for the tail summary. */
    private static final class PerAlgorithmAggregate {
        final String name;
        int count;
        long totalRelaxations;
        final List<Double> timesMs = new ArrayList<>();
        int foundCount;
        final List<Double> distancesWhenFound = new ArrayList<>();

        PerAlgorithmAggregate(String name) {
            this.name = name;
        }

        void record(PathfindingResult result, long relaxations, double ms) {
            count++;
            totalRelaxations += relaxations;
            timesMs.add(ms);
            if (result.isPathFound()) {
                foundCount++;
                distancesWhenFound.add(result.getTotalDistanceMeters());
            } else {
                distancesWhenFound.add(Double.NaN);
            }
        }

        double avgRelaxations() { return count == 0 ? 0 : (double) totalRelaxations / count; }
        double avgTimeMs() { return timesMs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0); }
        double medianTimeMs() {
            if (timesMs.isEmpty()) return 0.0;
            List<Double> sorted = new ArrayList<>(timesMs);
            sorted.sort(Double::compare);
            return sorted.get(sorted.size() / 2);
        }

        String correctnessVs(PerAlgorithmAggregate reference) {
            int mismatches = 0;
            int comparable = 0;
            int n = Math.min(distancesWhenFound.size(), reference.distancesWhenFound.size());
            for (int i = 0; i < n; i++) {
                double mine = distancesWhenFound.get(i);
                double ref = reference.distancesWhenFound.get(i);
                if (Double.isNaN(mine) || Double.isNaN(ref)) continue;
                comparable++;
                if (Math.abs(mine - ref) > 1e-6) mismatches++;
            }
            return comparable == 0 ? "n/a" : String.format("%d/%d match", comparable - mismatches, comparable);
        }
    }

    private record Config(Path geoJsonPath, Path cachePath, int pairCount, long seed, Path outPath) {
        static Config parse(String[] args) {
            Path geoJsonPath = Paths.get("..", "data", "full.geojson");
            Path cachePath = Paths.get("..", "data", "graph-cache.bin");
            int pairCount = 100;
            long seed = 42;
            Path outPath = null;

            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "--pairs" -> pairCount = Integer.parseInt(args[++i]);
                    case "--seed" -> seed = Long.parseLong(args[++i]);
                    case "--out" -> outPath = Paths.get(args[++i]);
                    case "--geojson" -> geoJsonPath = Paths.get(args[++i]);
                    case "--cache" -> cachePath = Paths.get(args[++i]);
                    default -> throw new IllegalArgumentException("Unknown argument: " + a);
                }
            }
            return new Config(geoJsonPath, cachePath, pairCount, seed, outPath);
        }
    }

    @SuppressWarnings("unused")
    private static String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }
}
