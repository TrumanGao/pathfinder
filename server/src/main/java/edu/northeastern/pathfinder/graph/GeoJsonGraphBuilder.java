package edu.northeastern.pathfinder.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Builds a routing graph from road LineString features in a GeoJSON FeatureCollection.
 *
 * Scope at current stage:
 * - read routable road LineString features only (identified by non-empty highway tag)
 * - split each LineString into segment-by-segment directed edges
 * - preserve raw metadata for future weight models
 * - keep missing values as null
 */
public final class GeoJsonGraphBuilder {
    private final ObjectMapper objectMapper;

    public GeoJsonGraphBuilder() {
        this(new ObjectMapper());
    }

    public GeoJsonGraphBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BuildResult build(Path geoJsonPath) throws IOException {
        JsonNode root = objectMapper.readTree(geoJsonPath.toFile());
        JsonNode features = root.path("features");

        Graph graph = new Graph();
        GraphBuildReport report = new GraphBuildReport();

        if (!features.isArray()) {
            return new BuildResult(graph, report);
        }

        for (JsonNode feature : features) {
            report.incrementFeaturesSeen();

            JsonNode geometry = feature.path("geometry");
            JsonNode properties = feature.path("properties");
            String geometryType = textOrNull(geometry.get("type"));
            String highway = textOrNull(properties.get("highway"));

            if (!"LineString".equals(geometryType) || isBlank(highway)) {
                report.incrementSkippedFeatures();
                continue;
            }

            JsonNode coordinates = geometry.path("coordinates");
            if (!coordinates.isArray() || coordinates.size() < 2) {
                report.incrementSkippedFeatures();
                continue;
            }

            report.incrementLineStringRoadFeatures();

            Map<String, Object> rawTags = copyRawProperties(properties);
            String sourceFeatureId = textOrNull(feature.get("id"));
            String maxspeedRaw = textOrNull(properties.get("maxspeed"));
            String onewayRaw = textOrNull(properties.get("oneway"));
            String roadName = textOrNull(properties.get("name"));

            DirectionPolicy directionPolicy = resolveDirectionPolicy(onewayRaw);

            for (int i = 0; i < coordinates.size() - 1; i++) {
                Coordinate from = parseCoordinate(coordinates.get(i));
                Coordinate to = parseCoordinate(coordinates.get(i + 1));
                if (from == null || to == null) {
                    continue;
                }

                graph.addNode(new Node(from.nodeId, from.lon, from.lat));
                graph.addNode(new Node(to.nodeId, to.lon, to.lat));

                double meters = haversineMeters(from.lat, from.lon, to.lat, to.lon);

                if (directionPolicy.emitForward) {
                    Edge forward = new Edge(
                            to.nodeId,
                            meters,
                            sourceFeatureId,
                            highway,
                            maxspeedRaw,
                            onewayRaw,
                            roadName,
                            rawTags
                    );
                    graph.addEdge(from.nodeId, forward);
                    report.incrementSegmentsBuilt();
                }

                if (directionPolicy.emitReverse) {
                    Edge reverse = new Edge(
                            from.nodeId,
                            meters,
                            sourceFeatureId,
                            highway,
                            maxspeedRaw,
                            onewayRaw,
                            roadName,
                            rawTags
                    );
                    graph.addEdge(to.nodeId, reverse);
                    report.incrementSegmentsBuilt();
                }
            }
        }

        return new BuildResult(graph, report);
    }

    /**
     * Current student-project construction policy:
     * - "-1" => reverse-only
     * - "yes"/"true"/"1" => forward-only
     * - "no"/"false"/"0" => bidirectional
     * - missing/unknown => bidirectional (temporary, to avoid over-pruning connectivity now)
     */
    private DirectionPolicy resolveDirectionPolicy(String onewayRaw) {
        if (onewayRaw == null || onewayRaw.isBlank()) {
            return DirectionPolicy.BIDIRECTIONAL;
        }

        String v = onewayRaw.trim().toLowerCase();
        if ("-1".equals(v)) {
            return DirectionPolicy.REVERSE_ONLY;
        }
        if ("yes".equals(v) || "true".equals(v) || "1".equals(v)) {
            return DirectionPolicy.FORWARD_ONLY;
        }
        if ("no".equals(v) || "false".equals(v) || "0".equals(v)) {
            return DirectionPolicy.BIDIRECTIONAL;
        }

        return DirectionPolicy.BIDIRECTIONAL;
    }

    private Coordinate parseCoordinate(JsonNode point) {
        if (point == null || !point.isArray() || point.size() < 2) {
            return null;
        }

        JsonNode lonNode = point.get(0);
        JsonNode latNode = point.get(1);
        if (lonNode == null || latNode == null || !lonNode.isNumber() || !latNode.isNumber()) {
            return null;
        }

        String lonRaw = lonNode.asText();
        String latRaw = latNode.asText();
        double lon = lonNode.asDouble();
        double lat = latNode.asDouble();

        String nodeId = NodeIdStrategy.fromCoordinate(lonRaw, latRaw);
        return new Coordinate(nodeId, lat, lon);
    }

    private Map<String, Object> copyRawProperties(JsonNode properties) {
        Map<String, Object> raw = new HashMap<>();
        if (properties == null || !properties.isObject()) {
            return raw;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            raw.put(field.getKey(), objectMapper.convertValue(field.getValue(), Object.class));
        }
        return raw;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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

    public static final class BuildResult {
        private final Graph graph;
        private final GraphBuildReport report;

        public BuildResult(Graph graph, GraphBuildReport report) {
            this.graph = graph;
            this.report = report;
        }

        public Graph getGraph() {
            return graph;
        }

        public GraphBuildReport getReport() {
            return report;
        }
    }

    private record Coordinate(String nodeId, double lat, double lon) {
    }

    private enum DirectionPolicy {
        FORWARD_ONLY(true, false),
        REVERSE_ONLY(false, true),
        BIDIRECTIONAL(true, true);

        private final boolean emitForward;
        private final boolean emitReverse;

        DirectionPolicy(boolean emitForward, boolean emitReverse) {
            this.emitForward = emitForward;
            this.emitReverse = emitReverse;
        }
    }
}
