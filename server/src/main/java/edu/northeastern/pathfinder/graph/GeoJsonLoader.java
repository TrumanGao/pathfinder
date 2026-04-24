package edu.northeastern.pathfinder.graph;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.pathfinder.service.SearchItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Single-pass streaming loader that reads the GeoJSON file once and produces
 * both the routing Graph and the SearchItem list, avoiding a double load.
 */
@Component
public class GeoJsonLoader {
    private static final Logger log = LoggerFactory.getLogger(GeoJsonLoader.class);

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s/,;:()\\[\\]{}\"'\\-_.#@!?&|+*=<>]+");
    private static final Pattern ACCENT_STRIP = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private static final List<String> SEARCH_METADATA_KEYS = List.of(
            "amenity", "highway", "shop", "tourism", "leisure", "building",
            "aeroway", "railway", "public_transport", "addr:street"
    );

    private final Graph graph;
    private final GraphBuildReport report;
    private final List<SearchItem> searchItems;

    /**
     * String intern pool to deduplicate highly-repeated strings across edges and nodes.
     * Typical highway values ("residential", "secondary", etc.) appear thousands of times.
     */
    private final ConcurrentHashMap<String, String> internPool = new ConcurrentHashMap<>();

    /**
     * Whitelist of raw tag keys retained on Edge objects for future cost models.
     * highway, maxspeed, oneway, name already live as dedicated Edge fields.
     */
    private static final Set<String> RETAINED_ROAD_TAGS = Set.of(
            "surface", "lanes", "access", "bridge", "tunnel",
            "toll", "junction", "lit", "bicycle", "foot",
            "motor_vehicle", "service", "footway", "tracktype",
            "smoothness", "incline", "width", "layer",
            "covered", "horse", "segregated"
    );

    public GeoJsonLoader(
            @Value("${pathfinder.graph.geojson-path:../data/full.geojson}") String geoJsonPath
    ) {
        Path path = Paths.get(geoJsonPath);
        Graph loadedGraph = new Graph();
        GraphBuildReport loadedReport = new GraphBuildReport();
        List<SearchItem> loadedItems = new ArrayList<>();

        if (Files.exists(path)) {
            try {
                load(path, loadedGraph, loadedReport, loadedItems);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load GeoJSON: " + geoJsonPath, e);
            }
        }

        this.graph = loadedGraph;
        this.report = loadedReport;
        this.searchItems = Collections.unmodifiableList(loadedItems);

        log.info("GeoJSON loaded: {} features seen, {} road features, {} segments, {} search items",
                report.getFeaturesSeen(), report.getLineStringRoadFeatures(),
                report.getSegmentsBuilt(), searchItems.size());
    }

    public Graph getGraph() {
        return graph;
    }

    public GraphBuildReport getReport() {
        return report;
    }

    public List<SearchItem> getSearchItems() {
        return searchItems;
    }

    /**
     * Stream-parses the GeoJSON FeatureCollection one feature at a time.
     * Peak memory holds a single feature's JsonNode instead of the whole file.
     */
    private void load(Path geoJsonPath, Graph graph, GraphBuildReport report, List<SearchItem> searchItems) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        int searchSequence = 1;

        try (JsonParser parser = mapper.getFactory().createParser(geoJsonPath.toFile())) {
            // Navigate to the "features" array
            if (!advanceToFeaturesArray(parser)) {
                return;
            }

            // Read features one by one
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                JsonNode feature = mapper.readTree(parser);
                report.incrementFeaturesSeen();

                // Try to build graph edges from this feature
                processGraphFeature(feature, graph, report);

                // Try to build search item from this feature
                SearchItem item = processSearchFeature(feature, searchSequence);
                if (item != null) {
                    searchItems.add(item);
                    searchSequence++;
                }
            }
        }
    }

    /**
     * Advance the streaming parser to the start of the "features" array.
     */
    private boolean advanceToFeaturesArray(JsonParser parser) throws IOException {
        while (parser.nextToken() != null) {
            if (parser.currentToken() == JsonToken.FIELD_NAME && "features".equals(parser.currentName())) {
                JsonToken next = parser.nextToken();
                return next == JsonToken.START_ARRAY;
            }
        }
        return false;
    }

    // ======================== Graph building ========================

    private void processGraphFeature(JsonNode feature, Graph graph, GraphBuildReport report) {
        JsonNode geometry = feature.path("geometry");
        JsonNode properties = feature.path("properties");
        String geometryType = textOrNull(geometry.get("type"));
        String highway = textOrNull(properties.get("highway"));

        if (!"LineString".equals(geometryType) || isBlank(highway)) {
            return;
        }

        JsonNode coordinates = geometry.path("coordinates");
        if (!coordinates.isArray() || coordinates.size() < 2) {
            return;
        }

        report.incrementLineStringRoadFeatures();

        highway = intern(highway);
        String maxspeedRaw = intern(textOrNull(properties.get("maxspeed")));
        String onewayRaw = intern(textOrNull(properties.get("oneway")));
        String roadName = intern(textOrNull(properties.get("name")));
        Map<String, Object> rawTags = copyRawPropertiesInterned(properties);

        DirectionPolicy directionPolicy = resolveDirectionPolicy(onewayRaw);

        for (int i = 0; i < coordinates.size() - 1; i++) {
            ParsedCoordinate from = parseCoordinate(coordinates.get(i));
            ParsedCoordinate to = parseCoordinate(coordinates.get(i + 1));
            if (from == null || to == null) {
                continue;
            }

            graph.addNode(new Node(from.nodeId, from.lon, from.lat));
            graph.addNode(new Node(to.nodeId, to.lon, to.lat));

            double meters = haversineMeters(from.lat, from.lon, to.lat, to.lon);

            if (directionPolicy.emitForward) {
                Edge forward = new Edge(to.nodeId, meters, highway, maxspeedRaw, onewayRaw, roadName, rawTags);
                graph.addEdge(from.nodeId, forward);
                report.incrementSegmentsBuilt();
            }

            if (directionPolicy.emitReverse) {
                Edge reverse = new Edge(from.nodeId, meters, highway, maxspeedRaw, onewayRaw, roadName, rawTags);
                graph.addEdge(to.nodeId, reverse);
                report.incrementSegmentsBuilt();
            }
        }
    }

    private DirectionPolicy resolveDirectionPolicy(String onewayRaw) {
        if (onewayRaw == null || onewayRaw.isBlank()) {
            return DirectionPolicy.BIDIRECTIONAL;
        }

        String v = onewayRaw.trim().toLowerCase(Locale.ROOT);
        if ("-1".equals(v)) return DirectionPolicy.REVERSE_ONLY;
        if ("yes".equals(v) || "true".equals(v) || "1".equals(v)) return DirectionPolicy.FORWARD_ONLY;
        if ("no".equals(v) || "false".equals(v) || "0".equals(v)) return DirectionPolicy.BIDIRECTIONAL;
        return DirectionPolicy.BIDIRECTIONAL;
    }

    private ParsedCoordinate parseCoordinate(JsonNode point) {
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

        String nodeId = intern(NodeIdStrategy.fromCoordinate(lonRaw, latRaw));
        return new ParsedCoordinate(nodeId, lat, lon);
    }

    /**
     * Copy only whitelisted raw properties, interning keys and string values.
     * Forward/reverse edges of the same feature share the same rawTags instance.
     */
    private Map<String, Object> copyRawPropertiesInterned(JsonNode properties) {
        if (properties == null || !properties.isObject()) {
            return Map.of();
        }

        Map<String, Object> raw = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            if (!RETAINED_ROAD_TAGS.contains(key)) {
                continue;
            }
            String internedKey = intern(key);
            JsonNode value = field.getValue();
            if (value.isNull()) {
                continue;
            } else if (value.isTextual()) {
                raw.put(internedKey, intern(value.asText()));
            } else if (value.isInt()) {
                raw.put(internedKey, value.asInt());
            } else if (value.isLong()) {
                raw.put(internedKey, value.asLong());
            } else if (value.isDouble() || value.isFloat()) {
                raw.put(internedKey, value.asDouble());
            } else if (value.isBoolean()) {
                raw.put(internedKey, value.asBoolean());
            } else {
                raw.put(internedKey, intern(value.asText()));
            }
        }
        return raw.isEmpty() ? Map.of() : Collections.unmodifiableMap(raw);
    }

    // ======================== Search item building ========================

    /**
     * Produces search items during the same loader pass by replicating
     * SearchService classification/filtering logic.
     */
    private SearchItem processSearchFeature(JsonNode feature, int sequence) {
        JsonNode propertiesNode = feature.path("properties");
        JsonNode geometryNode = feature.path("geometry");

        Coordinate coordinate = extractRepresentativeCoordinate(geometryNode);
        if (coordinate == null) {
            return null;
        }

        Map<String, String> tags = extractRelevantTags(propertiesNode);
        String name = normalizeText(textOrNull(propertiesNode.get("name")));
        if (!isMeaningfulName(name)) {
            return null;
        }

        SearchCategory category = classify(name, tags);
        if (category == null || "unknown".equals(category.type)) {
            return null;
        }
        if ("road".equals(category.type) && !tags.containsKey("highway")) {
            return null;
        }

        Map<String, String> metadata = extractMetadata(propertiesNode);
        List<String> tokens = buildSearchTokens(name, category.type, category.subType, metadata);
        String cuisine = normalizeTag(textOrNull(propertiesNode.get("cuisine")));
        List<String> sTags = buildStudentTags(name, category, tags, cuisine);

        return new SearchItem(
                sequence,
                name,
                name,
                category.type,
                category.subType,
                coordinate.lat,
                coordinate.lon,
                "geojson",
                metadata.containsKey("highway"),
                metadata,
                tokens,
                sTags
        );
    }

    // ======================== Search classification (from SearchService) ========================

    private static final Map<String, SearchCategory> AMENITY_MAP = Map.ofEntries(
            Map.entry("restaurant", new SearchCategory("food", "restaurant")),
            Map.entry("cafe", new SearchCategory("food", "cafe")),
            Map.entry("fast_food", new SearchCategory("food", "fast_food")),
            Map.entry("bar", new SearchCategory("food", "bar")),
            Map.entry("pub", new SearchCategory("food", "pub")),
            Map.entry("fuel", new SearchCategory("fuel", "fuel")),
            Map.entry("parking", new SearchCategory("parking", "parking")),
            Map.entry("hospital", new SearchCategory("healthcare", "hospital")),
            Map.entry("clinic", new SearchCategory("healthcare", "clinic")),
            Map.entry("pharmacy", new SearchCategory("healthcare", "pharmacy")),
            Map.entry("school", new SearchCategory("education", "school")),
            Map.entry("college", new SearchCategory("education", "college")),
            Map.entry("university", new SearchCategory("education", "university")),
            Map.entry("bus_station", new SearchCategory("transport", "bus_station"))
    );

    private static final Map<String, SearchCategory> AEROWAY_MAP = Map.ofEntries(
            Map.entry("aerodrome", new SearchCategory("transport", "airport")),
            Map.entry("terminal", new SearchCategory("transport", "airport_terminal")),
            Map.entry("gate", new SearchCategory("transport", "airport_gate"))
    );

    private static final Map<String, SearchCategory> PUBLIC_TRANSPORT_MAP = Map.ofEntries(
            Map.entry("station", new SearchCategory("transport", "station")),
            Map.entry("platform", new SearchCategory("transport", "platform")),
            Map.entry("stop_position", new SearchCategory("transport", "stop"))
    );

    private static final Map<String, SearchCategory> RAILWAY_MAP = Map.ofEntries(
            Map.entry("station", new SearchCategory("transport", "rail_station")),
            Map.entry("halt", new SearchCategory("transport", "rail_halt")),
            Map.entry("tram_stop", new SearchCategory("transport", "tram_stop"))
    );

    private static final Map<String, SearchCategory> TOURISM_MAP = Map.ofEntries(
            Map.entry("hotel", new SearchCategory("lodging", "hotel")),
            Map.entry("museum", new SearchCategory("poi", "museum")),
            Map.entry("attraction", new SearchCategory("poi", "attraction"))
    );

    private static final Map<String, SearchCategory> LEISURE_MAP = Map.ofEntries(
            Map.entry("stadium", new SearchCategory("recreation", "stadium")),
            Map.entry("park", new SearchCategory("recreation", "park")),
            Map.entry("sports_centre", new SearchCategory("recreation", "sports_centre"))
    );

    private SearchCategory classify(String name, Map<String, String> tags) {
        String amenity = normalizeTag(tags.get("amenity"));
        if (amenity != null) {
            SearchCategory c = AMENITY_MAP.get(amenity);
            if (c != null) return c;
        }

        String aeroway = normalizeTag(tags.get("aeroway"));
        if (aeroway != null) {
            SearchCategory c = AEROWAY_MAP.get(aeroway);
            if (c != null) return c;
        }

        String publicTransport = normalizeTag(tags.get("public_transport"));
        if (publicTransport != null) {
            SearchCategory c = PUBLIC_TRANSPORT_MAP.get(publicTransport);
            if (c != null) return c;
        }

        String railway = normalizeTag(tags.get("railway"));
        if (railway != null) {
            SearchCategory c = RAILWAY_MAP.get(railway);
            if (c != null) return c;
        }

        String shop = normalizeTag(tags.get("shop"));
        if (shop != null) {
            return new SearchCategory("shop", shop);
        }

        String tourism = normalizeTag(tags.get("tourism"));
        if (tourism != null) {
            SearchCategory c = TOURISM_MAP.get(tourism);
            return c != null ? c : new SearchCategory("poi", tourism);
        }

        String leisure = normalizeTag(tags.get("leisure"));
        if (leisure != null) {
            SearchCategory c = LEISURE_MAP.get(leisure);
            return c != null ? c : new SearchCategory("recreation", leisure);
        }

        String highway = normalizeTag(tags.get("highway"));
        if (highway != null && isMeaningfulName(name)) {
            return new SearchCategory("road", highway);
        }

        String building = normalizeTag(tags.get("building"));
        if (building != null && isMeaningfulName(name)) {
            return new SearchCategory("place", building);
        }

        return new SearchCategory("unknown", "unknown");
    }

    // ======================== Geometry helpers (from SearchService) ========================

    private Coordinate extractRepresentativeCoordinate(JsonNode geometryNode) {
        String geometryType = textOrNull(geometryNode.get("type"));
        JsonNode coordinatesNode = geometryNode.path("coordinates");

        if ("Point".equals(geometryType)) return coordinateFromPosition(coordinatesNode);
        if ("LineString".equals(geometryType)) return lineStringMidpoint(coordinatesNode);
        if ("Polygon".equals(geometryType) || "MultiPolygon".equals(geometryType)) return boundingBoxCenter(coordinatesNode);
        if ("MultiPoint".equals(geometryType) || "MultiLineString".equals(geometryType)) return boundingBoxCenter(coordinatesNode);
        return null;
    }

    private Coordinate coordinateFromPosition(JsonNode positionNode) {
        if (positionNode == null || !positionNode.isArray() || positionNode.size() < 2) return null;
        if (!positionNode.get(0).isNumber() || !positionNode.get(1).isNumber()) return null;
        return new Coordinate(positionNode.get(1).asDouble(), positionNode.get(0).asDouble());
    }

    private Coordinate lineStringMidpoint(JsonNode coordinatesNode) {
        if (coordinatesNode == null || !coordinatesNode.isArray() || coordinatesNode.isEmpty()) return null;
        int size = coordinatesNode.size();
        if (size % 2 == 1) return coordinateFromPosition(coordinatesNode.get(size / 2));
        Coordinate left = coordinateFromPosition(coordinatesNode.get((size / 2) - 1));
        Coordinate right = coordinateFromPosition(coordinatesNode.get(size / 2));
        if (left == null || right == null) return null;
        return new Coordinate((left.lat + right.lat) / 2.0, (left.lon + right.lon) / 2.0);
    }

    private Coordinate boundingBoxCenter(JsonNode coordinatesNode) {
        List<double[]> positions = new ArrayList<>();
        collectPositions(coordinatesNode, positions);
        if (positions.isEmpty()) return null;
        double minLon = Double.POSITIVE_INFINITY, maxLon = Double.NEGATIVE_INFINITY;
        double minLat = Double.POSITIVE_INFINITY, maxLat = Double.NEGATIVE_INFINITY;
        for (double[] pos : positions) {
            minLon = Math.min(minLon, pos[0]);
            maxLon = Math.max(maxLon, pos[0]);
            minLat = Math.min(minLat, pos[1]);
            maxLat = Math.max(maxLat, pos[1]);
        }
        return new Coordinate((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0);
    }

    private void collectPositions(JsonNode node, List<double[]> positions) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isArray()) return;
        if (node.size() >= 2 && node.get(0).isNumber() && node.get(1).isNumber()) {
            positions.add(new double[]{node.get(0).asDouble(), node.get(1).asDouble()});
            return;
        }
        for (JsonNode child : node) {
            collectPositions(child, positions);
        }
    }

    // ======================== Student tags ========================

    private static final Set<String> ASIAN_CUISINES = Set.of(
            "chinese", "japanese", "korean", "thai", "vietnamese", "asian",
            "indian", "sushi", "ramen", "noodle", "pho", "dim_sum",
            "taiwanese", "filipino", "malaysian", "indonesian", "nepali",
            "pakistani", "bangladeshi", "sri_lankan", "burmese"
    );

    private static final Set<String> HALAL_CUISINES = Set.of(
            "halal", "kebab", "mediterranean", "middle_eastern", "turkish",
            "afghan", "persian", "lebanese", "arab", "moroccan"
    );

    /** Builds lifestyle tags for international students from cuisine, category, and name. */
    private List<String> buildStudentTags(String name, SearchCategory category, Map<String, String> tags, String cuisine) {
        List<String> result = new ArrayList<>();
        String nameLower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String amenity = normalizeTag(tags.get("amenity"));
        String shop = normalizeTag(tags.get("shop"));

        // Asian food
        if (cuisine != null) {
            for (String part : cuisine.split("[;,]")) {
                if (ASIAN_CUISINES.contains(part.trim())) {
                    result.add("asian_food");
                    break;
                }
            }
        }
        if (!result.contains("asian_food") && (nameLower.contains("asian") || nameLower.contains("china")
                || nameLower.contains("chinese") || nameLower.contains("pho") || nameLower.contains("sushi")
                || nameLower.contains("ramen") || nameLower.contains("korean") || nameLower.contains("thai"))) {
            result.add("asian_food");
        }

        // Halal
        if (cuisine != null) {
            for (String part : cuisine.split("[;,]")) {
                if (HALAL_CUISINES.contains(part.trim())) {
                    result.add("halal");
                    break;
                }
            }
        }
        if (!result.contains("halal") && nameLower.contains("halal")) {
            result.add("halal");
        }

        // Bubble tea
        if ((cuisine != null && cuisine.contains("bubble_tea")) || nameLower.contains("boba")
                || nameLower.contains("bubble") || nameLower.contains("kung fu tea")
                || nameLower.contains("gong cha") || nameLower.contains("tiger sugar")) {
            result.add("bubble_tea");
        }

        // Affordable (fast food)
        if ("fast_food".equals(category.subType)) {
            result.add("affordable");
        }

        // Grocery
        if ("supermarket".equals(shop) || "convenience".equals(shop)) {
            result.add("grocery");
        }

        // Laundry
        if ("dry_cleaning".equals(shop) || "laundry".equals(shop)) {
            result.add("laundry");
        }

        // Bank / ATM
        if ("bank".equals(amenity) || "atm".equals(amenity)) {
            result.add("bank_atm");
        }

        // Pharmacy
        if ("pharmacy".equals(amenity)) {
            result.add("pharmacy");
        }

        // Place of worship
        if ("place_of_worship".equals(amenity)) {
            result.add("worship");
        }

        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    // ======================== Tokenization ========================

    /**
     * Pre-computes search tokens from name, type, subType, and metadata.
     * Tokens are lowercased, accent-stripped, and split on whitespace/punctuation.
     */
    private List<String> buildSearchTokens(String name, String type, String subType, Map<String, String> metadata) {
        List<String> tokens = new ArrayList<>();
        tokenize(name, tokens);
        tokenize(type, tokens);
        tokenize(subType, tokens);
        if (metadata != null) {
            for (String value : metadata.values()) {
                tokenize(value, tokens);
            }
        }
        return List.copyOf(tokens);
    }

    private void tokenize(String text, List<String> out) {
        if (text == null || text.isBlank()) return;
        String normalized = ACCENT_STRIP.matcher(
                Normalizer.normalize(text, Normalizer.Form.NFD)
        ).replaceAll("").toLowerCase(Locale.ROOT);
        for (String token : TOKEN_SPLIT.split(normalized)) {
            if (!token.isEmpty()) {
                out.add(token);
            }
        }
    }

    // ======================== Tag / text helpers ========================

    private Map<String, String> extractRelevantTags(JsonNode propertiesNode) {
        Map<String, String> tags = new LinkedHashMap<>();
        for (String key : SEARCH_METADATA_KEYS) {
            String value = normalizeText(textOrNull(propertiesNode.get(key)));
            if (value != null) tags.put(key, value);
        }
        return tags;
    }

    private Map<String, String> extractMetadata(JsonNode propertiesNode) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (String key : SEARCH_METADATA_KEYS) {
            String value = normalizeText(textOrNull(propertiesNode.get(key)));
            if (value != null) metadata.put(key, value);
        }
        return metadata;
    }

    private String intern(String s) {
        if (s == null) return null;
        return internPool.computeIfAbsent(s, k -> k);
    }

    private String normalizeTag(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isMeaningfulName(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return !"null".equals(normalized) && !"none".equals(normalized)
                && !"n/a".equals(normalized) && !"unknown".equals(normalized)
                && !"unnamed".equals(normalized) && !"unnamed road".equals(normalized);
    }

    private String normalizeText(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) return null;
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

    private record ParsedCoordinate(String nodeId, double lat, double lon) {}
    private record Coordinate(double lat, double lon) {}
    private record SearchCategory(String type, String subType) {}

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
