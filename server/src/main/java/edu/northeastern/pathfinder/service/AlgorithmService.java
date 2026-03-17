package edu.northeastern.pathfinder.service;

import edu.northeastern.pathfinder.graph.GeoJsonGraphBuilder;
import edu.northeastern.pathfinder.graph.Graph;
import edu.northeastern.pathfinder.model.AlgorithmRequest;
import edu.northeastern.pathfinder.model.AlgorithmResponse;
import edu.northeastern.pathfinder.model.Node;
import edu.northeastern.pathfinder.pathfinding.AStarShortestPath;
import edu.northeastern.pathfinder.pathfinding.DijkstraShortestPath;
import edu.northeastern.pathfinder.pathfinding.PathfindingResult;
import edu.northeastern.pathfinder.pathfinding.ShortestPathAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal orchestration service for frontend API integration.
 */
@Service
public class AlgorithmService {
    private final Graph graph;

    private final ShortestPathAlgorithm astar = new AStarShortestPath();
    private final ShortestPathAlgorithm dijkstra = new DijkstraShortestPath();

    private final List<PoiEntry> pois;

    private final double south;
    private final double north;
    private final double west;
    private final double east;

    public AlgorithmService(
            @Value("${pathfinder.graph.geojson-path:../data/full.geojson}") String geoJsonPath,
            @Value("${pathfinder.poi.csv-path:../data/pois.csv}") String poiCsvPath
    ) {
        try {
            this.graph = new GeoJsonGraphBuilder().build(Paths.get(geoJsonPath)).getGraph();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load graph from GeoJSON: " + geoJsonPath, e);
        }

        this.pois = loadPois(Paths.get(poiCsvPath));

        double s = Double.POSITIVE_INFINITY;
        double n = Double.NEGATIVE_INFINITY;
        double w = Double.POSITIVE_INFINITY;
        double e = Double.NEGATIVE_INFINITY;
        for (edu.northeastern.pathfinder.graph.Node node : graph.getNodesById().values()) {
            s = Math.min(s, node.getLat());
            n = Math.max(n, node.getLat());
            w = Math.min(w, node.getLon());
            e = Math.max(e, node.getLon());
        }
        this.south = s;
        this.north = n;
        this.west = w;
        this.east = e;
    }

    /**
     * Compatibility endpoint retained from previous integration.
     */
    public AlgorithmResponse findRoute(AlgorithmRequest request) {
        String startNodeId = findNearestNodeId(request.getStartLat(), request.getStartLon());
        String endNodeId = findNearestNodeId(request.getEndLat(), request.getEndLon());

        String algo = normalizeAlgorithm(request.getAlgorithm());
        ShortestPathAlgorithm selected = "dijkstra".equals(algo) ? dijkstra : astar;

        long t0 = System.nanoTime();
        PathfindingResult result = selected.findPath(graph, startNodeId, endNodeId);
        long t1 = System.nanoTime();

        AlgorithmResponse response = new AlgorithmResponse();
        response.setAlgorithm(algo);
        response.setStartNodeId(startNodeId);
        response.setEndNodeId(endNodeId);
        response.setPathFound(result.isPathFound());
        response.setTotalDistanceMeters(result.isPathFound() ? result.getTotalDistanceMeters() : Double.POSITIVE_INFINITY);
        response.setRuntimeMs((t1 - t0) / 1_000_000L);
        response.setPath(toPathNodes(result.getPathNodeIds()));
        return response;
    }

    /**
     * GET /api/map-info
     */
    public Map<String, Object> getMapInfo() {
        Map<String, Object> mapBounds = new LinkedHashMap<>();
        mapBounds.put("south", south);
        mapBounds.put("north", north);
        mapBounds.put("west", west);
        mapBounds.put("east", east);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("mapBounds", mapBounds);
        return response;
    }

    /**
     * POST /api/path-finding/compare
     *
     * Current backend supports Dijkstra + A* only (no BFS).
     */
    public Map<String, Object> comparePath(AlgorithmRequest request) {
        String startNodeId = findNearestNodeId(request.getStartLat(), request.getStartLon());
        String endNodeId = findNearestNodeId(request.getEndLat(), request.getEndLon());

        long allStart = System.nanoTime();
        AlgoRun d = runAlgo("Dijkstra", dijkstra, startNodeId, endNodeId);
        AlgoRun a = runAlgo("A*", astar, startNodeId, endNodeId);
        long allEnd = System.nanoTime();

        List<AlgoRun> runs = new ArrayList<>(List.of(d, a));
        runs.sort(Comparator.comparingLong(AlgoRun::timeMs));

        String fastest = runs.get(0).name();
        long totalComputeMs = (allEnd - allStart) / 1_000_000L;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalComputeMs", totalComputeMs);
        summary.put("fastestAlgo", fastest);

        List<String> algoOrder = List.of("Dijkstra", "A*");

        Map<String, Object> resultsByAlgo = new LinkedHashMap<>();
        resultsByAlgo.put("Dijkstra", toCompareAlgoPayload(d));
        resultsByAlgo.put("A*", toCompareAlgoPayload(a));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("summary", summary);
        response.put("algoOrder", algoOrder);
        response.put("resultsByAlgo", resultsByAlgo);
        return response;
    }

    /**
     * GET /api/poi-search?q=...
     */
    public Map<String, Object> searchPoi(String q, int limit) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        int boundedLimit = Math.max(1, Math.min(limit, 50));

        List<Map<String, Object>> results = new ArrayList<>();
        for (PoiEntry poi : pois) {
            if (!needle.isEmpty() && !poi.name().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", poi.name());
            item.put("lng", poi.lon());
            item.put("lat", poi.lat());
            results.add(item);
            if (results.size() >= boundedLimit) {
                break;
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results);
        return response;
    }

    /**
     * GET /api/node-info?lng=...&lat=...
     */
    public Map<String, Object> nearestNodeInfo(double lat, double lon) {
        String nodeId = findNearestNodeId(lat, lon);
        edu.northeastern.pathfinder.graph.Node node = graph.getNode(nodeId)
                .orElseThrow(() -> new IllegalStateException("Nearest node not found: " + nodeId));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("nodeId", node.getNodeId());
        response.put("lng", node.getLon());
        response.put("lat", node.getLat());
        response.put("name", null);
        response.put("tags", Map.of());
        return response;
    }

    private AlgoRun runAlgo(String name, ShortestPathAlgorithm algo, String startNodeId, String endNodeId) {
        long t0 = System.nanoTime();
        PathfindingResult result = algo.findPath(graph, startNodeId, endNodeId);
        long t1 = System.nanoTime();
        return new AlgoRun(name, result, (t1 - t0) / 1_000_000L);
    }

    private Map<String, Object> toCompareAlgoPayload(AlgoRun run) {
        List<List<Double>> path = toPolyline(run.result().getPathNodeIds());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("timeMs", run.timeMs());
        details.put("distance", run.result().isPathFound() ? run.result().getTotalDistanceMeters() : Double.POSITIVE_INFINITY);
        // Current stage does not track explored-node count inside algorithms.
        details.put("nodesExplored", path.size());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("path", path);
        payload.put("visited", List.of());
        payload.put("details", details);
        return payload;
    }

    private List<List<Double>> toPolyline(List<String> nodeIds) {
        List<List<Double>> line = new ArrayList<>();
        for (String nodeId : nodeIds) {
            graph.getNode(nodeId).ifPresent(n -> line.add(List.of(n.getLon(), n.getLat())));
        }
        return line;
    }

    private String normalizeAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            return "astar";
        }
        String lower = algorithm.trim().toLowerCase(Locale.ROOT);
        if ("dijkstra".equals(lower)) {
            return "dijkstra";
        }
        return "astar";
    }

    /**
     * Simple nearest-node matching for current graph size (~12k nodes).
     */
    private String findNearestNodeId(double lat, double lon) {
        String bestId = null;
        double best = Double.POSITIVE_INFINITY;

        for (edu.northeastern.pathfinder.graph.Node node : graph.getNodesById().values()) {
            double d = haversineMeters(lat, lon, node.getLat(), node.getLon());
            if (d < best) {
                best = d;
                bestId = node.getNodeId();
            }
        }

        if (bestId == null) {
            throw new IllegalStateException("Graph has no nodes");
        }
        return bestId;
    }

    private List<Node> toPathNodes(List<String> nodeIds) {
        List<Node> path = new ArrayList<>();
        for (String nodeId : nodeIds) {
            graph.getNode(nodeId).ifPresent(n -> path.add(new Node(n.getNodeId(), n.getLat(), n.getLon())));
        }
        return path;
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

    private List<PoiEntry> loadPois(Path path) {
        List<PoiEntry> list = new ArrayList<>();
        if (!Files.exists(path)) {
            return list;
        }

        try (BufferedReader br = Files.newBufferedReader(path)) {
            String header = br.readLine();
            if (header == null) {
                return list;
            }

            String[] columns = header.split(",");
            int nameIdx = indexOf(columns, "name");
            int lonIdx = indexOf(columns, "lon");
            int latIdx = indexOf(columns, "lat");
            if (nameIdx < 0 || lonIdx < 0 || latIdx < 0) {
                return list;
            }

            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length <= Math.max(nameIdx, Math.max(lonIdx, latIdx))) {
                    continue;
                }
                String name = parts[nameIdx].trim();
                if (name.isEmpty()) {
                    continue;
                }
                try {
                    double lon = Double.parseDouble(parts[lonIdx].trim());
                    double lat = Double.parseDouble(parts[latIdx].trim());
                    list.add(new PoiEntry(name, lat, lon));
                } catch (NumberFormatException ignored) {
                    // skip malformed line
                }
            }
        } catch (IOException ignored) {
            // keep empty list
        }

        return list;
    }

    private int indexOf(String[] columns, String name) {
        for (int i = 0; i < columns.length; i++) {
            if (name.equalsIgnoreCase(columns[i].trim())) {
                return i;
            }
        }
        return -1;
    }

    private record AlgoRun(String name, PathfindingResult result, long timeMs) {
    }

    private record PoiEntry(String name, double lat, double lon) {
    }
}
