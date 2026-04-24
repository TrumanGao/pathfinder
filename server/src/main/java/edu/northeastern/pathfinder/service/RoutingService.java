package edu.northeastern.pathfinder.service;

import edu.northeastern.pathfinder.config.RoutingProperties;
import edu.northeastern.pathfinder.graph.Edge;
import edu.northeastern.pathfinder.graph.Graph;
import edu.northeastern.pathfinder.pathfinding.AStarShortestPath;
import edu.northeastern.pathfinder.pathfinding.BidirectionalDijkstra;
import edu.northeastern.pathfinder.pathfinding.DijkstraShortestPath;
import edu.northeastern.pathfinder.pathfinding.PathCostModel;
import edu.northeastern.pathfinder.pathfinding.PathfindingResult;
import edu.northeastern.pathfinder.pathfinding.ShortestPathAlgorithm;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Orchestrates routing: resolves endpoints, builds a PathCostModel,
 * selects A* / Dijkstra, and returns the computed path with metadata.
 */
@Service
public class RoutingService {
    private final Graph graph;
    private final NearestNodeService nearestNodeService;
    private final RoutingProperties properties;
    private final RoutingCostPolicy routingCostPolicy;
    private final SpeedResolver speedResolver;
    private final ShortestPathAlgorithm astar = new AStarShortestPath();
    private final ShortestPathAlgorithm dijkstra = new DijkstraShortestPath();
    private final ShortestPathAlgorithm bidirectional = new BidirectionalDijkstra();
    private final GraphBounds bounds;

    public RoutingService(
            Graph graph,
            NearestNodeService nearestNodeService,
            RoutingProperties properties,
            RoutingCostPolicy routingCostPolicy,
            SpeedResolver speedResolver
    ) {
        this.graph = graph;
        this.nearestNodeService = nearestNodeService;
        this.properties = properties;
        this.routingCostPolicy = routingCostPolicy;
        this.speedResolver = speedResolver;
        this.bounds = calculateBounds();
    }

    public RouteComputation route(RouteQuery query) {
        ResolvedLocation start = resolveLocation(query.startNodeId(), query.startLat(), query.startLon());
        ResolvedLocation end = resolveLocation(query.endNodeId(), query.endLat(), query.endLon());
        String algorithm = normalizeAlgorithm(query.algorithm());
        RoutingOptions options = resolveOptions(query);

        ShortestPathAlgorithm selected = selectAlgorithm(algorithm);
        PathCostModel costModel = routingCostPolicy.create(options);

        long t0 = System.nanoTime();
        PathfindingResult pathResult = selected.findPath(graph, start.nodeId(), end.nodeId(), costModel);
        long t1 = System.nanoTime();

        double distanceMeters = pathResult.isPathFound() ? computePathDistanceMeters(pathResult.getPathNodeIds()) : Double.POSITIVE_INFINITY;
        double estimatedTravelTimeSeconds = pathResult.isPathFound()
                ? computePathTravelTimeSeconds(pathResult.getPathNodeIds())
                : Double.POSITIVE_INFINITY;

        return new RouteComputation(
                algorithm,
                options,
                start,
                end,
                pathResult,
                distanceMeters,
                estimatedTravelTimeSeconds,
                (t1 - t0) / 1_000_000L
        );
    }

    public NodeReference getNodeReferenceByGraphNodeId(String graphNodeId) {
        var graphNode = graph.getNode(graphNodeId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown graph nodeId: " + graphNodeId));
        return new NodeReference(graphNodeId, graphNode.getLat(), graphNode.getLon());
    }

    public List<NodeReference> toNodeReferences(List<String> graphNodeIds) {
        List<NodeReference> nodes = new ArrayList<>();
        for (String graphNodeId : graphNodeIds) {
            nodes.add(getNodeReferenceByGraphNodeId(graphNodeId));
        }
        return nodes;
    }

    public ResolvedLocation resolveLocation(String nodeId, Double lat, Double lon) {
        if (nodeId != null && !nodeId.isBlank()) {
            NodeReference node = getNodeReferenceByGraphNodeId(nodeId.trim());
            return new ResolvedLocation(node.nodeId(), node.lat(), node.lon(), 0.0);
        }
        if (lat != null && lon != null) {
            NearestNodeService.NearestNodeMatch nearest = nearestNodeService.findNearestNode(lat, lon);
            NodeReference node = getNodeReferenceByGraphNodeId(nearest.nodeId());
            return new ResolvedLocation(node.nodeId(), node.lat(), node.lon(), nearest.distanceMeters());
        }
        throw new IllegalArgumentException("Location is missing nodeId or coordinates");
    }

    public GraphBounds getBounds() {
        return bounds;
    }

    public int getNodeCount() {
        return graph.getNodeCount();
    }

    public int getEdgeCount() {
        return graph.getEdgeCount();
    }

    public String getDefaultAlgorithm() {
        return normalizeAlgorithm(properties.getDefaultAlgorithm());
    }

    public List<String> getSupportedAlgorithms() {
        return List.of("astar", "dijkstra", "bidirectional");
    }

    public String getDefaultObjective() {
        return resolveDefaultObjective().apiValue();
    }

    public List<String> getSupportedObjectives() {
        return List.of(
                RoutingObjective.DISTANCE.apiValue(),
                RoutingObjective.TIME.apiValue(),
                RoutingObjective.BALANCED.apiValue(),
                RoutingObjective.SAFE_WALK.apiValue()
        );
    }

    public RoutingOptions.BalancedWeights getDefaultBalancedWeights() {
        return new RoutingOptions.BalancedWeights(
                properties.getBalanced().getDistanceWeight(),
                properties.getBalanced().getTimeWeight()
        );
    }

    public List<String> getSupportedRoadPreferences() {
        return List.of("avoidHighway", "preferMainRoad");
    }

    private RoutingOptions resolveOptions(RouteQuery query) {
        RoutingObjective objective = RoutingObjective.fromValue(query.objective(), resolveDefaultObjective());
        RoutingOptions.BalancedWeights weights = resolveWeights(query);
        RoutingOptions.RoadPreferences roadPreferences = new RoutingOptions.RoadPreferences(
                query.avoidHighway(),
                query.preferMainRoad()
        );
        return new RoutingOptions(objective, weights, roadPreferences);
    }

    private RoutingOptions.BalancedWeights resolveWeights(RouteQuery query) {
        double defaultDistanceWeight = properties.getBalanced().getDistanceWeight();
        double defaultTimeWeight = properties.getBalanced().getTimeWeight();

        double distanceWeight = query.distanceWeight() == null ? defaultDistanceWeight : query.distanceWeight();
        double timeWeight = query.timeWeight() == null ? defaultTimeWeight : query.timeWeight();
        return new RoutingOptions.BalancedWeights(distanceWeight, timeWeight);
    }

    private RoutingObjective resolveDefaultObjective() {
        return RoutingObjective.fromValue(properties.getDefaultObjective(), RoutingObjective.DISTANCE);
    }

    private double computePathDistanceMeters(List<String> pathNodeIds) {
        if (pathNodeIds.size() < 2) {
            return 0.0;
        }

        double total = 0.0;
        for (int i = 0; i < pathNodeIds.size() - 1; i++) {
            total += lookupBestEdge(pathNodeIds.get(i), pathNodeIds.get(i + 1)).getSegmentDistanceMeters();
        }
        return total;
    }

    /**
     * Estimated travel time derived from the final path using the same
     * speed-resolution rules as time-based routing. Lightweight estimate only.
     */
    private double computePathTravelTimeSeconds(List<String> pathNodeIds) {
        if (pathNodeIds.size() < 2) {
            return 0.0;
        }

        double total = 0.0;
        for (int i = 0; i < pathNodeIds.size() - 1; i++) {
            Edge edge = lookupBestEdge(pathNodeIds.get(i), pathNodeIds.get(i + 1));
            total += edge.getSegmentDistanceMeters() / speedResolver.resolveMetersPerSecond(edge);
        }
        return total;
    }

    private Edge lookupBestEdge(String fromNodeId, String toNodeId) {
        Edge bestEdge = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Edge edge : graph.getOutgoing(fromNodeId)) {
            if (toNodeId.equals(edge.getToNodeId()) && edge.getSegmentDistanceMeters() < bestDistance) {
                bestDistance = edge.getSegmentDistanceMeters();
                bestEdge = edge;
            }
        }
        if (bestEdge != null) {
            return bestEdge;
        }
        throw new IllegalStateException("Path references missing edge from " + fromNodeId + " to " + toNodeId);
    }

    private GraphBounds calculateBounds() {
        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;

        for (var node : graph.getNodesById().values()) {
            minLat = Math.min(minLat, node.getLat());
            maxLat = Math.max(maxLat, node.getLat());
            minLon = Math.min(minLon, node.getLon());
            maxLon = Math.max(maxLon, node.getLon());
        }

        return new GraphBounds(minLat, maxLat, minLon, maxLon);
    }

    private String normalizeAlgorithm(String value) {
        if (value == null || value.isBlank()) {
            return getDefaultAlgorithmValue();
        }

        String lower = value.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "dijkstra" -> "dijkstra";
            case "astar" -> "astar";
            case "bidirectional", "bidi", "bidirectional-dijkstra" -> "bidirectional";
            default -> getDefaultAlgorithmValue();
        };
    }

    private ShortestPathAlgorithm selectAlgorithm(String algorithm) {
        return switch (algorithm) {
            case "dijkstra" -> dijkstra;
            case "bidirectional" -> bidirectional;
            default -> astar;
        };
    }

    private String getDefaultAlgorithmValue() {
        String configured = properties.getDefaultAlgorithm();
        if (configured == null) return "astar";
        String lower = configured.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "dijkstra" -> "dijkstra";
            case "bidirectional", "bidi", "bidirectional-dijkstra" -> "bidirectional";
            default -> "astar";
        };
    }

    public record RouteQuery(
            String startNodeId,
            String endNodeId,
            Double startLat,
            Double startLon,
            Double endLat,
            Double endLon,
            String algorithm,
            String objective,
            Double distanceWeight,
            Double timeWeight,
            boolean avoidHighway,
            boolean preferMainRoad
    ) {
    }

    public record RouteComputation(
            String algorithm,
            RoutingOptions options,
            ResolvedLocation start,
            ResolvedLocation end,
            PathfindingResult pathResult,
            double distanceMeters,
            double estimatedTravelTimeSeconds,
            long durationMs
    ) {
    }

    public record NodeReference(String nodeId, double lat, double lon) {
    }

    public record ResolvedLocation(String nodeId, double lat, double lon, double snapDistanceM) {
    }

    public record GraphBounds(double minLat, double maxLat, double minLon, double maxLon) {
    }
}
