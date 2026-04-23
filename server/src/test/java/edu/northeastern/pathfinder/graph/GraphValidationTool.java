package edu.northeastern.pathfinder.graph;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight utility to validate graph construction quality at current stage.
 *
 * Usage:
 *   java ... GraphValidationTool [path-to-geojson]
 *
 * If no argument is provided, defaults to ../data/full.geojson when run from server directory.
 */
public final class GraphValidationTool {
    private static final int DEFAULT_SAMPLE_NODE_COUNT = 5;
    private static final int DEFAULT_SAMPLE_EDGE_COUNT = 3;

    private GraphValidationTool() {
    }

    public static void main(String[] args) throws IOException {
        Path geoJsonPath = args.length > 0
                ? Paths.get(args[0])
                : Paths.get("..", "data", "full.geojson");

        GeoJsonGraphBuilder builder = new GeoJsonGraphBuilder();
        GeoJsonGraphBuilder.BuildResult result = builder.build(geoJsonPath);

        Graph graph = result.getGraph();
        GraphBuildReport report = result.getReport();

        ValidationSummary summary = summarize(graph, report);
        printSummary(geoJsonPath, summary, graph);
    }

    public static ValidationSummary summarize(Graph graph, GraphBuildReport report) {
        Map<String, Integer> incomingCounts = new HashMap<>();
        int selfLoopEdges = 0;

        for (Map.Entry<String, List<Edge>> entry : graph.getOutgoingAdj().entrySet()) {
            String fromNodeId = entry.getKey();
            for (Edge edge : entry.getValue()) {
                if (fromNodeId.equals(edge.getToNodeId())) {
                    selfLoopEdges++;
                }
                incomingCounts.merge(edge.getToNodeId(), 1, Integer::sum);
            }
        }

        int isolatedNodes = 0;
        int noOutgoingNodes = 0;
        int noIncomingNodes = 0;

        for (String nodeId : graph.getNodesById().keySet()) {
            int outgoing = graph.getOutgoing(nodeId).size();
            int incoming = incomingCounts.getOrDefault(nodeId, 0);

            if (outgoing == 0) {
                noOutgoingNodes++;
            }
            if (incoming == 0) {
                noIncomingNodes++;
            }
            if (outgoing == 0 && incoming == 0) {
                isolatedNodes++;
            }
        }

        return new ValidationSummary(
                graph.getNodeCount(),
                graph.getEdgeCount(),
                report.getLineStringRoadFeatures(),
                report.getFeaturesSeen(),
                report.getSkippedFeatures(),
                report.getSegmentsBuilt(),
                isolatedNodes,
                noOutgoingNodes,
                noIncomingNodes,
                selfLoopEdges
        );
    }

    private static void printSummary(Path geoJsonPath, ValidationSummary summary, Graph graph) {
        System.out.println("=== Graph Validation Summary ===");
        System.out.println("Source: " + geoJsonPath.toAbsolutePath());
        System.out.println("Nodes: " + summary.nodeCount());
        System.out.println("Edges: " + summary.edgeCount());
        System.out.println("Routable LineString features processed: " + summary.routableFeaturesProcessed());
        System.out.println("Total features seen: " + summary.totalFeaturesSeen());
        System.out.println("Skipped features: " + summary.skippedFeatures());
        System.out.println("Segments built: " + summary.segmentsBuilt());
        System.out.println("Isolated nodes (in=0,out=0): " + summary.isolatedNodes());
        System.out.println("Nodes with no outgoing edges: " + summary.noOutgoingNodes());
        System.out.println("Nodes with no incoming edges: " + summary.noIncomingNodes());
        System.out.println("Self-loop edges: " + summary.selfLoopEdges());
        System.out.println();

        printSampleNodes(graph, DEFAULT_SAMPLE_NODE_COUNT, DEFAULT_SAMPLE_EDGE_COUNT);

        if (summary.isolatedNodes() > 0 || summary.selfLoopEdges() > 0) {
            System.out.println();
            System.out.println("Potentially suspicious topology detected. Review counts above.");
        }
    }

    private static void printSampleNodes(Graph graph, int sampleNodeCount, int sampleEdgeCount) {
        List<Node> nodes = new ArrayList<>(graph.getNodesById().values());
        nodes.sort(Comparator.comparing(Node::getNodeId));

        int limit = Math.min(sampleNodeCount, nodes.size());
        System.out.println("Sample nodes and outgoing edges (up to " + limit + " nodes):");

        for (int i = 0; i < limit; i++) {
            Node node = nodes.get(i);
            List<Edge> outgoing = graph.getOutgoing(node.getNodeId());

            System.out.println("- Node " + node.getNodeId() + " (lon=" + node.getLon() + ", lat=" + node.getLat() + ")");
            if (outgoing.isEmpty()) {
                System.out.println("  outgoing: none");
                continue;
            }

            int edgeLimit = Math.min(sampleEdgeCount, outgoing.size());
            for (int j = 0; j < edgeLimit; j++) {
                Edge e = outgoing.get(j);
                System.out.println("  -> " + e.getToNodeId()
                        + " | distM=" + Math.round(e.getSegmentDistanceMeters())
                        + " | highway=" + e.getHighway()
                        + " | onewayRaw=" + e.getOnewayRaw());
            }

            if (outgoing.size() > edgeLimit) {
                System.out.println("  ... (" + (outgoing.size() - edgeLimit) + " more outgoing edges)");
            }
        }
    }

    /**
     * Minimal summary object for quick inspection and future unit assertions.
     */
    public record ValidationSummary(
            int nodeCount,
            int edgeCount,
            int routableFeaturesProcessed,
            int totalFeaturesSeen,
            int skippedFeatures,
            int segmentsBuilt,
            int isolatedNodes,
            int noOutgoingNodes,
            int noIncomingNodes,
            int selfLoopEdges
    ) {
    }
}
