package edu.northeastern.pathfinder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.pathfinder.config.SearchProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Builds a normalized search index from GeoJSON features.
 * Supports substring matching, type filtering, and a stable type/subType classification.
 */
@Service
public class SearchService {
    private static final List<String> METADATA_KEYS = List.of(
            "amenity", "highway", "shop", "tourism", "leisure", "building",
            "aeroway", "railway", "public_transport", "addr:street"
    );

    /** Ordered rules mapping OSM-like tags into stable (type, subType) categories. */
    private static final List<ClassificationRule> CLASSIFICATION_RULES = List.of(
            ClassificationRule.byValue("amenity", Map.ofEntries(
                    Map.entry("restaurant", SearchCategory.of("food", "restaurant")),
                    Map.entry("cafe", SearchCategory.of("food", "cafe")),
                    Map.entry("fast_food", SearchCategory.of("food", "fast_food")),
                    Map.entry("bar", SearchCategory.of("food", "bar")),
                    Map.entry("pub", SearchCategory.of("food", "pub")),
                    Map.entry("fuel", SearchCategory.of("fuel", "fuel")),
                    Map.entry("parking", SearchCategory.of("parking", "parking")),
                    Map.entry("hospital", SearchCategory.of("healthcare", "hospital")),
                    Map.entry("clinic", SearchCategory.of("healthcare", "clinic")),
                    Map.entry("pharmacy", SearchCategory.of("healthcare", "pharmacy")),
                    Map.entry("school", SearchCategory.of("education", "school")),
                    Map.entry("college", SearchCategory.of("education", "college")),
                    Map.entry("university", SearchCategory.of("education", "university")),
                    Map.entry("bus_station", SearchCategory.of("transport", "bus_station"))
            )),
            ClassificationRule.byValue("aeroway", Map.ofEntries(
                    Map.entry("aerodrome", SearchCategory.of("transport", "airport")),
                    Map.entry("terminal", SearchCategory.of("transport", "airport_terminal")),
                    Map.entry("gate", SearchCategory.of("transport", "airport_gate"))
            )),
            ClassificationRule.byValue("public_transport", Map.ofEntries(
                    Map.entry("station", SearchCategory.of("transport", "station")),
                    Map.entry("platform", SearchCategory.of("transport", "platform")),
                    Map.entry("stop_position", SearchCategory.of("transport", "stop"))
            )),
            ClassificationRule.byValue("railway", Map.ofEntries(
                    Map.entry("station", SearchCategory.of("transport", "rail_station")),
                    Map.entry("halt", SearchCategory.of("transport", "rail_halt")),
                    Map.entry("tram_stop", SearchCategory.of("transport", "tram_stop"))
            )),
            ClassificationRule.byPrefix("shop", "shop"),
            ClassificationRule.byValueOrFallback("tourism", Map.ofEntries(
                    Map.entry("hotel", SearchCategory.of("lodging", "hotel")),
                    Map.entry("museum", SearchCategory.of("poi", "museum")),
                    Map.entry("attraction", SearchCategory.of("poi", "attraction"))
            ), "poi"),
            ClassificationRule.byValueOrFallback("leisure", Map.ofEntries(
                    Map.entry("stadium", SearchCategory.of("recreation", "stadium")),
                    Map.entry("park", SearchCategory.of("recreation", "park")),
                    Map.entry("sports_centre", SearchCategory.of("recreation", "sports_centre"))
            ), "recreation"),
            ClassificationRule.highwayRule(),
            ClassificationRule.buildingRule()
    );

    private final SearchProperties properties;
    private final ObjectMapper objectMapper;
    private final List<SearchItem> items;

    public SearchService(
            SearchProperties properties,
            @Value("${pathfinder.graph.geojson-path:../data/full.geojson}") String geoJsonPath
    ) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.items = Collections.unmodifiableList(loadItems(Paths.get(geoJsonPath)));
    }

    public List<SearchItem> search(String query, List<String> types, Integer limit) {
        String needle = normalizeSearchToken(query);
        Set<String> normalizedTypes = normalizeTypes(types);
        int boundedLimit = normalizeLimit(limit);

        List<SearchItem> results = new ArrayList<>();
        for (SearchItem item : items) {
            if (!normalizedTypes.isEmpty() && !normalizedTypes.contains(item.type())) {
                continue;
            }
            if (needle != null && !matches(item, needle)) {
                continue;
            }
            results.add(item);
            if (results.size() >= boundedLimit) {
                break;
            }
        }
        return results;
    }

    public int getDefaultLimit() {
        return properties.getDefaultLimit();
    }

    public int getMaxLimit() {
        return properties.getMaxLimit();
    }

    public List<String> getSupportedTypes() {
        return new ArrayList<>(new TreeSet<>(
                items.stream().map(SearchItem::type).toList()
        ));
    }

    private int normalizeLimit(Integer limit) {
        int resolved = limit == null ? properties.getDefaultLimit() : limit;
        return Math.max(1, Math.min(resolved, properties.getMaxLimit()));
    }

    private boolean matches(SearchItem item, String needle) {
        if (item.name().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        if (item.displayName().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        if (item.type().toLowerCase(Locale.ROOT).contains(needle) || item.subType().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        for (String value : item.metadata().values()) {
            if (value.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> normalizeTypes(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }

        TreeSet<String> normalized = new TreeSet<>();
        for (String value : values) {
            String token = normalizeSearchToken(value);
            if (token != null) {
                normalized.add(token);
            }
        }
        return normalized;
    }

    private List<SearchItem> loadItems(Path geoJsonPath) {
        if (!Files.exists(geoJsonPath)) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(geoJsonPath.toFile());
            JsonNode features = root.path("features");
            if (!features.isArray()) {
                return List.of();
            }

            List<SearchItem> loaded = new ArrayList<>();
            int sequence = 1;

            for (JsonNode feature : features) {
                JsonNode propertiesNode = feature.path("properties");
                GeoJsonFeatureContext context = buildFeatureContext(feature, propertiesNode);
                if (context == null) {
                    continue;
                }

                SearchCategory category = classify(context);
                if (!shouldInclude(context, category)) {
                    continue;
                }

                int itemId = sequence++;
                Map<String, String> metadata = extractMetadata(propertiesNode);
                loaded.add(new SearchItem(
                        itemId,
                        context.name(),
                        context.name(),
                        category.type(),
                        category.subType(),
                        context.coordinate().lat(),
                        context.coordinate().lon(),
                        "geojson",
                        metadata.containsKey("highway"),
                        metadata
                ));
            }

            return loaded;
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Picks a representative coordinate:
     * Point -> self; LineString -> midpoint; Polygon / Multi* -> bounding-box center.
     */
    private Coordinate extractRepresentativeCoordinate(JsonNode geometryNode) {
        String geometryType = textOrNull(geometryNode.get("type"));
        JsonNode coordinatesNode = geometryNode.path("coordinates");

        if ("Point".equals(geometryType)) {
            return coordinateFromPosition(coordinatesNode);
        }
        if ("LineString".equals(geometryType)) {
            return lineStringMidpoint(coordinatesNode);
        }
        if ("Polygon".equals(geometryType) || "MultiPolygon".equals(geometryType)) {
            return boundingBoxCenter(coordinatesNode);
        }
        if ("MultiPoint".equals(geometryType) || "MultiLineString".equals(geometryType)) {
            return boundingBoxCenter(coordinatesNode);
        }
        return null;
    }

    /** Keeps named roads / places / buildings / POIs that map to a known category. */
    private boolean shouldInclude(GeoJsonFeatureContext context, SearchCategory category) {
        if (!isMeaningfulName(context.name())) {
            return false;
        }
        if (category == null) {
            return false;
        }
        if ("unknown".equals(category.type())) {
            return false;
        }
        if ("road".equals(category.type())) {
            return context.tags().containsKey("highway");
        }
        return true;
    }

    private GeoJsonFeatureContext buildFeatureContext(JsonNode feature, JsonNode propertiesNode) {
        Coordinate coordinate = extractRepresentativeCoordinate(feature.path("geometry"));
        if (coordinate == null) {
            return null;
        }

        Map<String, String> tags = extractRelevantTags(propertiesNode);
        String name = normalizeText(textOrNull(propertiesNode.get("name")));
        if (!isMeaningfulName(name)) {
            return null;
        }

        return new GeoJsonFeatureContext(name, coordinate, tags);
    }

    private SearchCategory classify(GeoJsonFeatureContext context) {
        for (ClassificationRule rule : CLASSIFICATION_RULES) {
            SearchCategory category = rule.classify(context);
            if (category != null) {
                return category;
            }
        }
        return SearchCategory.of("unknown", "unknown");
    }

    private Map<String, String> extractRelevantTags(JsonNode propertiesNode) {
        Map<String, String> tags = new LinkedHashMap<>();
        for (String key : METADATA_KEYS) {
            String value = normalizeText(textOrNull(propertiesNode.get(key)));
            if (value != null) {
                tags.put(key, value);
            }
        }
        return tags;
    }

    private Map<String, String> extractMetadata(JsonNode propertiesNode) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (String key : METADATA_KEYS) {
            String value = normalizeText(textOrNull(propertiesNode.get(key)));
            if (value != null) {
                metadata.put(key, value);
            }
        }
        return metadata;
    }

    private Coordinate coordinateFromPosition(JsonNode positionNode) {
        if (positionNode == null || !positionNode.isArray() || positionNode.size() < 2) {
            return null;
        }
        if (!positionNode.get(0).isNumber() || !positionNode.get(1).isNumber()) {
            return null;
        }
        return new Coordinate(positionNode.get(1).asDouble(), positionNode.get(0).asDouble());
    }

    private Coordinate lineStringMidpoint(JsonNode coordinatesNode) {
        if (coordinatesNode == null || !coordinatesNode.isArray() || coordinatesNode.isEmpty()) {
            return null;
        }

        int size = coordinatesNode.size();
        if (size % 2 == 1) {
            return coordinateFromPosition(coordinatesNode.get(size / 2));
        }

        Coordinate left = coordinateFromPosition(coordinatesNode.get((size / 2) - 1));
        Coordinate right = coordinateFromPosition(coordinatesNode.get(size / 2));
        if (left == null || right == null) {
            return null;
        }
        return new Coordinate((left.lat() + right.lat()) / 2.0, (left.lon() + right.lon()) / 2.0);
    }

    private Coordinate boundingBoxCenter(JsonNode coordinatesNode) {
        List<double[]> positions = new ArrayList<>();
        collectPositions(coordinatesNode, positions);
        if (positions.isEmpty()) {
            return null;
        }

        double minLon = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;

        for (double[] position : positions) {
            minLon = Math.min(minLon, position[0]);
            maxLon = Math.max(maxLon, position[0]);
            minLat = Math.min(minLat, position[1]);
            maxLat = Math.max(maxLat, position[1]);
        }

        return new Coordinate((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0);
    }

    private void collectPositions(JsonNode node, List<double[]> positions) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isArray()) {
            return;
        }

        if (node.size() >= 2 && node.get(0).isNumber() && node.get(1).isNumber()) {
            positions.add(new double[]{node.get(0).asDouble(), node.get(1).asDouble()});
            return;
        }

        for (JsonNode child : node) {
            collectPositions(child, positions);
        }
    }

    private boolean isMeaningfulName(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return !"null".equals(normalized)
                && !"none".equals(normalized)
                && !"n/a".equals(normalized)
                && !"unknown".equals(normalized)
                && !"unnamed".equals(normalized)
                && !"unnamed road".equals(normalized);
    }

    private String normalizeSearchToken(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record Coordinate(double lat, double lon) {
    }

    private record GeoJsonFeatureContext(String name, Coordinate coordinate, Map<String, String> tags) {
    }

    private record SearchCategory(String type, String subType) {
        private static SearchCategory of(String type, String subType) {
            return new SearchCategory(type, subType);
        }
    }

    private record ClassificationRule(Function<GeoJsonFeatureContext, SearchCategory> classifier) {
        private SearchCategory classify(GeoJsonFeatureContext context) {
            return classifier.apply(context);
        }

        private static ClassificationRule byValue(String key, Map<String, SearchCategory> mapping) {
            return new ClassificationRule(context -> {
                String value = normalizeTagValue(context.tags().get(key));
                return value == null ? null : mapping.get(value);
            });
        }

        private static ClassificationRule byValueOrFallback(String key, Map<String, SearchCategory> mapping, String fallbackType) {
            return new ClassificationRule(context -> {
                String value = normalizeTagValue(context.tags().get(key));
                if (value == null) {
                    return null;
                }
                SearchCategory mapped = mapping.get(value);
                return mapped != null ? mapped : SearchCategory.of(fallbackType, value);
            });
        }

        private static ClassificationRule byPrefix(String key, String type) {
            return new ClassificationRule(context -> {
                String value = normalizeTagValue(context.tags().get(key));
                return value == null ? null : SearchCategory.of(type, value);
            });
        }

        private static ClassificationRule highwayRule() {
            return new ClassificationRule(context -> {
                String highway = normalizeTagValue(context.tags().get("highway"));
                if (highway == null || !isMeaningfulName(context.name())) {
                    return null;
                }
                return SearchCategory.of("road", highway);
            });
        }

        private static ClassificationRule buildingRule() {
            return new ClassificationRule(context -> {
                String building = normalizeTagValue(context.tags().get("building"));
                if (building == null || !isMeaningfulName(context.name())) {
                    return null;
                }
                return SearchCategory.of("place", building);
            });
        }

        private static String normalizeTagValue(String value) {
            return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
        }

        private static boolean isMeaningfulName(String value) {
            if (value == null || value.isBlank()) {
                return false;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            return !"null".equals(normalized)
                    && !"none".equals(normalized)
                    && !"n/a".equals(normalized)
                    && !"unknown".equals(normalized)
                    && !"unnamed".equals(normalized)
                    && !"unnamed road".equals(normalized);
        }
    }
}
