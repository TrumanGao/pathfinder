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
 * Minimal backend orchestration aligned to the frontend API contract.
 */
@Service
public class AlgorithmService {
    private final Graph graph;
    private final ShortestPathAlgorithm astar = new AStarShortestPath();
    private final ShortestPathAlgorithm dijkstra = new DijkstraShortestPath();

    /** Deterministic API-facing node id mapping (int <-> graph node id string). */
    private final Map<String, Integer> apiNodeIdByGraphNodeId;
    private final Map<Integer, String> graphNodeIdByApiNodeId;

    private final List<PoiEntry> pois;

    private final double minLat;
    private final double maxLat;
    private final double minLon;
    private final double maxLon;

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

        List<String> sortedGraphIds = new ArrayList<>(graph.getNodesById().keySet());
        sortedGraphIds.sort(Comparator.naturalOrder());
        this.apiNodeIdByGraphNodeId = new HashMap<>(sortedGraphIds.size());
        this.graphNodeIdByApiNodeId = new HashMap<>(sortedGraphIds.size());
        for (int i = 0; i < sortedGraphIds.size(); i++) {
            int apiId = i + 1;
            String graphId = sortedGraphIds.get(i);
            apiNodeIdByGraphNodeId.put(graphId, apiId);
            graphNodeIdByApiNodeId.put(apiId, graphId);
        }

        double south = Double.POSITIVE_INFINITY;
        double north = Double.NEGATIVE_INFINITY;
        double west = Double.POSITIVE_INFINITY;
        double east = Double.NEGATIVE_INFINITY;
        for (edu.northeastern.pathfinder.graph.Node node : graph.getNodesById().values()) {
            south = Math.min(south, node.getLat());
            north = Math.max(north, node.getLat());
            west = Math.min(west, node.getLon());
            east = Math.max(east, node.getLon());
        }
        this.minLat = south;
        this.maxLat = north;
        this.minLon = west;
        this.maxLon = east;
    }

    /**
     * POST /api/route
     * Request: {startNodeId, endNodeId, algorithms[]}
     * Response: {results:[...]}
     */
    public AlgorithmResponse route(AlgorithmRequest request) {
        String startGraphNodeId = resolveGraphNodeIdFromRequestStart(request);
        String endGraphNodeId = resolveGraphNodeIdFromRequestEnd(request);

        List<String> requestedAlgorithms = request.getAlgorithms() == null || request.getAlgorithms().isEmpty()
                ? List.of("astar")
                : request.getAlgorithms();

        List<AlgorithmResponse.AlgorithmResult> results = new ArrayList<>();
        for (String algoRaw : requestedAlgorithms) {
            String algo = normalizeAlgorithm(algoRaw);
            ShortestPathAlgorithm selected = selectAlgorithm(algo);

            long t0 = System.nanoTime();
            PathfindingResult pathResult = selected.findPath(graph, startGraphNodeId, endGraphNodeId);
            long t1 = System.nanoTime();

            List<Node> path = toApiNodes(pathResult.getPathNodeIds());
            List<Node> visitedOrder = new ArrayList<>(path); // lightweight placeholder for current stage

            AlgorithmResponse.AlgorithmResult result = new AlgorithmResponse.AlgorithmResult();
            result.setAlgorithm(algo);
            result.setPath(path);
            result.setVisitedOrder(visitedOrder);
            result.setDistanceM(pathResult.isPathFound() ? pathResult.getTotalDistanceMeters() : Double.POSITIVE_INFINITY);
            result.setDurationMs((t1 - t0) / 1_000_000L);
            result.setVisitedCount(visitedOrder.size());
            results.add(result);
        }

        AlgorithmResponse response = new AlgorithmResponse();
        response.setResults(results);
        return response;
    }

    /**
     * GET /api/map-info
     * Response: { bounds:{minLat,maxLat,minLon,maxLon}, nodeCount, edgeCount }
     */
    public Map<String, Object> getMapInfo() {
        Map<String, Object> bounds = new LinkedHashMap<>();
        bounds.put("minLat", minLat);
        bounds.put("maxLat", maxLat);
        bounds.put("minLon", minLon);
        bounds.put("maxLon", maxLon);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("bounds", bounds);
        response.put("nodeCount", graph.getNodeCount());
        response.put("edgeCount", graph.getEdgeCount());
        return response;
    }

    /**
     * GET /api/poi-search?q=...&limit=...
     * Response: POI[] (array)
     */
    public List<Map<String, Object>> searchPoi(String q, int limit) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        int boundedLimit = Math.max(1, Math.min(limit, 50));

        List<Map<String, Object>> results = new ArrayList<>();
        for (PoiEntry poi : pois) {
            if (!needle.isEmpty() && !matchesPoi(poi, needle)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("poiId", poi.poiId());
            item.put("name", poi.name());
            item.put("lat", poi.lat());
            item.put("lon", poi.lon());
            item.put("amenity", poi.amenity());
            item.put("tourism", poi.tourism());
            item.put("shop", poi.shop());
            item.put("leisure", poi.leisure());
            item.put("addrStreet", poi.addrStreet());
            results.add(item);
            if (results.size() >= boundedLimit) {
                break;
            }
        }
        return results;
    }

    /**
     * GET /api/nearest-node?lat=...&lon=...
     * Response: {nodeId, lat, lon, distanceM}
     */
    public Map<String, Object> nearestNode(double lat, double lon) {
        Nearest nearest = findNearestNode(lat, lon);
        edu.northeastern.pathfinder.graph.Node node = graph.getNode(nearest.graphNodeId())
                .orElseThrow(() -> new IllegalStateException("Nearest node not found: " + nearest.graphNodeId()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("nodeId", apiNodeIdByGraphNodeId.get(nearest.graphNodeId()));
        response.put("lat", node.getLat());
        response.put("lon", node.getLon());
        response.put("distanceM", nearest.distanceMeters());
        return response;
    }

    private String resolveGraphNodeIdFromRequestStart(AlgorithmRequest request) {
        if (request.getStartNodeId() != null) {
            return resolveGraphNodeId(request.getStartNodeId());
        }
        return findNearestNode(request.getStartLat(), request.getStartLon()).graphNodeId();
    }

    private String resolveGraphNodeIdFromRequestEnd(AlgorithmRequest request) {
        if (request.getEndNodeId() != null) {
            return resolveGraphNodeId(request.getEndNodeId());
        }
        return findNearestNode(request.getEndLat(), request.getEndLon()).graphNodeId();
    }

    private String resolveGraphNodeId(int apiNodeId) {
        if (apiNodeId < 0) {
            PoiEntry poi = findPoiById(Math.abs(apiNodeId));
            return findNearestNode(poi.lat(), poi.lon()).graphNodeId();
        }

        String graphNodeId = graphNodeIdByApiNodeId.get(apiNodeId);
        if (graphNodeId == null) {
            throw new IllegalArgumentException("Unknown nodeId: " + apiNodeId);
        }
        return graphNodeId;
    }

    private PoiEntry findPoiById(int poiId) {
        for (PoiEntry poi : pois) {
            if (poi.poiId() == poiId) {
                return poi;
            }
        }
        throw new IllegalArgumentException("Unknown poiId: " + poiId);
    }

    private String normalizeAlgorithm(String value) {
        if (value == null || value.isBlank()) {
            return "astar";
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "dijkstra" -> "dijkstra";
            case "astar" -> "astar";
            case "bibfs" -> "bibfs";
            default -> "astar";
        };
    }

    private ShortestPathAlgorithm selectAlgorithm(String algorithm) {
        if ("dijkstra".equals(algorithm)) {
            return dijkstra;
        }
        if ("bibfs".equals(algorithm)) {
            // Bi-BFS is not implemented in current stage. Keep API compatibility with a lightweight fallback.
            return dijkstra;
        }
        return astar;
    }

    private List<Node> toApiNodes(List<String> graphNodeIds) {
        List<Node> path = new ArrayList<>();
        for (String graphNodeId : graphNodeIds) {
            edu.northeastern.pathfinder.graph.Node graphNode = graph.getNode(graphNodeId)
                    .orElseThrow(() -> new IllegalStateException("Missing graph node: " + graphNodeId));
            Integer apiNodeId = apiNodeIdByGraphNodeId.get(graphNodeId);
            if (apiNodeId == null) {
                throw new IllegalStateException("Missing API node id mapping for graph node: " + graphNodeId);
            }
            path.add(new Node(apiNodeId, graphNode.getLat(), graphNode.getLon()));
        }
        return path;
    }

    private Nearest findNearestNode(double lat, double lon) {
        String bestGraphNodeId = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (edu.northeastern.pathfinder.graph.Node node : graph.getNodesById().values()) {
            double d = haversineMeters(lat, lon, node.getLat(), node.getLon());
            if (d < bestDistance) {
                bestDistance = d;
                bestGraphNodeId = node.getNodeId();
            }
        }

        if (bestGraphNodeId == null) {
            throw new IllegalStateException("Graph has no nodes");
        }
        return new Nearest(bestGraphNodeId, bestDistance);
    }

    private boolean matchesPoi(PoiEntry poi, String needle) {
        return poi.name().toLowerCase(Locale.ROOT).contains(needle)
                || poi.amenity().toLowerCase(Locale.ROOT).contains(needle)
                || poi.tourism().toLowerCase(Locale.ROOT).contains(needle)
                || poi.shop().toLowerCase(Locale.ROOT).contains(needle)
                || poi.leisure().toLowerCase(Locale.ROOT).contains(needle)
                || poi.addrStreet().toLowerCase(Locale.ROOT).contains(needle);
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

            String[] columns = header.split(",", -1);
            int nameIdx = indexOf(columns, "name");
            int lonIdx = firstIndexOf(columns, "lon", "lng", "longitude");
            int latIdx = firstIndexOf(columns, "lat", "latitude");
            int amenityIdx = indexOf(columns, "amenity");
            int tourismIdx = indexOf(columns, "tourism");
            int shopIdx = indexOf(columns, "shop");
            int leisureIdx = indexOf(columns, "leisure");
            int streetIdx = firstIndexOf(columns, "addr:street", "addrStreet", "street");
            if (nameIdx < 0 || lonIdx < 0 || latIdx < 0) {
                return list;
            }

            int poiId = 1;
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length <= Math.max(nameIdx, Math.max(lonIdx, latIdx))) {
                    continue;
                }
                String name = safeField(parts, nameIdx);
                if (name.isEmpty()) {
                    continue;
                }
                try {
                    double lon = Double.parseDouble(safeField(parts, lonIdx));
                    double lat = Double.parseDouble(safeField(parts, latIdx));
                    list.add(new PoiEntry(
                            poiId++,
                            name,
                            lat,
                            lon,
                            safeField(parts, amenityIdx),
                            safeField(parts, tourismIdx),
                            safeField(parts, shopIdx),
                            safeField(parts, leisureIdx),
                            safeField(parts, streetIdx)
                    ));
                } catch (NumberFormatException ignored) {
                    // skip malformed row
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

    private int firstIndexOf(String[] columns, String... names) {
        for (String name : names) {
            int idx = indexOf(columns, name);
            if (idx >= 0) {
                return idx;
            }
        }
        return -1;
    }

    private String safeField(String[] parts, int idx) {
        if (idx < 0 || idx >= parts.length) {
            return "";
        }
        return parts[idx].trim();
    }

    private record PoiEntry(
            int poiId,
            String name,
            double lat,
            double lon,
            String amenity,
            String tourism,
            String shop,
            String leisure,
            String addrStreet
    ) {
    }

    private record Nearest(String graphNodeId, double distanceMeters) {
    }
}
