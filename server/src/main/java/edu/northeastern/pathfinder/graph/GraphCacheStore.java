package edu.northeastern.pathfinder.graph;

import edu.northeastern.pathfinder.service.SearchItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Binary serializer for the in-memory routing graph and search index.
 *
 * The format is a single file: a fixed header, a shared string table, the
 * graph (nodes + outgoing adjacency with edge attributes and raw tags), and
 * the search index. A single string table dedupes highly repeated values
 * like OSM highway classes and road names across millions of edges.
 *
 * Cache validity is keyed on the source GeoJSON's size and mtime — fast
 * enough to check on every startup, strict enough to catch real changes.
 * Hash-based keys are deliberately avoided because rehashing a 900MB
 * GeoJSON would cost as much as we're trying to save.
 */
final class GraphCacheStore {
    private static final Logger log = LoggerFactory.getLogger(GraphCacheStore.class);

    private static final long MAGIC = 0x5046434143484531L; // "PFCACHE1"
    private static final int VERSION = 1;

    private static final byte TAG_STRING = 0;
    private static final byte TAG_INT = 1;
    private static final byte TAG_LONG = 2;
    private static final byte TAG_DOUBLE = 3;
    private static final byte TAG_BOOL = 4;

    private GraphCacheStore() {}

    /**
     * Load a previously written cache if it matches the current source file.
     * Returns null if the cache is absent, stale, corrupt, or a version mismatch.
     */
    static Bundle readIfFresh(Path cachePath, Path sourceGeoJson) {
        if (!Files.exists(cachePath) || !Files.exists(sourceGeoJson)) {
            return null;
        }

        long expectedSize;
        long expectedMtime;
        try {
            expectedSize = Files.size(sourceGeoJson);
            expectedMtime = Files.getLastModifiedTime(sourceGeoJson).toMillis();
        } catch (IOException e) {
            return null;
        }

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(cachePath), 1 << 20))) {
            long magic = in.readLong();
            if (magic != MAGIC) {
                log.warn("Graph cache magic mismatch, ignoring cache at {}", cachePath);
                return null;
            }
            int version = in.readInt();
            if (version != VERSION) {
                log.info("Graph cache version {} does not match expected {}, rebuilding", version, VERSION);
                return null;
            }
            long cachedSize = in.readLong();
            long cachedMtime = in.readLong();
            if (cachedSize != expectedSize || cachedMtime != expectedMtime) {
                log.info("Source GeoJSON changed (size/mtime differ), rebuilding graph cache");
                return null;
            }

            long t0 = System.nanoTime();

            // String table
            int stringCount = in.readInt();
            String[] strings = new String[stringCount];
            for (int i = 0; i < stringCount; i++) {
                strings[i] = in.readUTF();
            }

            Graph graph = new Graph();

            // Nodes
            int nodeCount = in.readInt();
            for (int i = 0; i < nodeCount; i++) {
                String nodeId = strings[in.readInt()];
                double lon = in.readDouble();
                double lat = in.readDouble();
                graph.addNode(new Node(nodeId, lon, lat));
            }

            // Adjacency
            int adjSourceCount = in.readInt();
            for (int i = 0; i < adjSourceCount; i++) {
                String fromId = strings[in.readInt()];
                int edgeCount = in.readInt();
                for (int e = 0; e < edgeCount; e++) {
                    String toId = strings[in.readInt()];
                    double distance = in.readDouble();
                    String highway = readNullableString(in, strings);
                    String maxspeedRaw = readNullableString(in, strings);
                    String onewayRaw = readNullableString(in, strings);
                    String roadName = readNullableString(in, strings);
                    Map<String, Object> rawTags = readRawTags(in, strings);
                    Edge edge = new Edge(toId, distance, highway, maxspeedRaw, onewayRaw, roadName, rawTags);
                    graph.addEdge(fromId, edge);
                }
            }

            // Search items
            int itemCount = in.readInt();
            List<SearchItem> items = new ArrayList<>(itemCount);
            for (int i = 0; i < itemCount; i++) {
                int id = in.readInt();
                String name = readNullableString(in, strings);
                String displayName = readNullableString(in, strings);
                String type = readNullableString(in, strings);
                String subType = readNullableString(in, strings);
                double lat = in.readDouble();
                double lon = in.readDouble();
                String source = readNullableString(in, strings);
                boolean routable = in.readBoolean();

                int metaCount = in.readInt();
                Map<String, String> metadata;
                if (metaCount == 0) {
                    metadata = Map.of();
                } else {
                    metadata = new LinkedHashMap<>(metaCount * 2);
                    for (int m = 0; m < metaCount; m++) {
                        String key = strings[in.readInt()];
                        String value = strings[in.readInt()];
                        metadata.put(key, value);
                    }
                    metadata = Collections.unmodifiableMap(metadata);
                }

                int tokenCount = in.readInt();
                List<String> tokens = tokenCount == 0 ? List.of() : new ArrayList<>(tokenCount);
                for (int t = 0; t < tokenCount; t++) {
                    tokens.add(strings[in.readInt()]);
                }
                if (tokenCount != 0) tokens = List.copyOf(tokens);

                int tagCount = in.readInt();
                List<String> studentTags = tagCount == 0 ? List.of() : new ArrayList<>(tagCount);
                for (int t = 0; t < tagCount; t++) {
                    studentTags.add(strings[in.readInt()]);
                }
                if (tagCount != 0) studentTags = List.copyOf(studentTags);

                items.add(new SearchItem(id, name, displayName, type, subType,
                        lat, lon, source, routable, metadata, tokens, studentTags));
            }

            // Report counters
            GraphBuildReport report = new GraphBuildReport();
            int featuresSeen = in.readInt();
            int lineStringRoadFeatures = in.readInt();
            int skippedFeatures = in.readInt();
            int segmentsBuilt = in.readInt();
            for (int i = 0; i < featuresSeen; i++) report.incrementFeaturesSeen();
            for (int i = 0; i < lineStringRoadFeatures; i++) report.incrementLineStringRoadFeatures();
            for (int i = 0; i < skippedFeatures; i++) report.incrementSkippedFeatures();
            for (int i = 0; i < segmentsBuilt; i++) report.incrementSegmentsBuilt();

            long durationMs = (System.nanoTime() - t0) / 1_000_000L;
            log.info("Graph cache loaded in {} ms: {} nodes, {} edges, {} search items",
                    durationMs, graph.getNodeCount(), graph.getEdgeCount(), items.size());

            return new Bundle(graph, Collections.unmodifiableList(items), report);
        } catch (IOException e) {
            log.warn("Failed to read graph cache at {}, rebuilding", cachePath, e);
            return null;
        }
    }

    /**
     * Persist the graph and search index atomically (write to .tmp, then rename).
     */
    static void write(Path cachePath, Path sourceGeoJson, Graph graph, List<SearchItem> items, GraphBuildReport report) {
        Objects.requireNonNull(cachePath, "cachePath");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(items, "items");

        long sourceSize;
        long sourceMtime;
        try {
            sourceSize = Files.size(sourceGeoJson);
            sourceMtime = Files.getLastModifiedTime(sourceGeoJson).toMillis();
        } catch (IOException e) {
            log.warn("Cannot stat source GeoJSON, skipping cache write", e);
            return;
        }

        StringTable table = new StringTable();
        collectStrings(graph, items, table);

        Path parent = cachePath.toAbsolutePath().getParent();
        Path tempPath;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            tempPath = Files.createTempFile(parent, "graph-cache-", ".tmp");
        } catch (IOException e) {
            log.warn("Unable to prepare graph cache directory at {}", cachePath, e);
            return;
        }

        long t0 = System.nanoTime();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tempPath), 1 << 20))) {
            out.writeLong(MAGIC);
            out.writeInt(VERSION);
            out.writeLong(sourceSize);
            out.writeLong(sourceMtime);

            // String table
            out.writeInt(table.size());
            for (String s : table.ordered()) {
                out.writeUTF(s);
            }

            // Nodes
            Map<String, Node> nodes = graph.getNodesById();
            out.writeInt(nodes.size());
            for (Node node : nodes.values()) {
                out.writeInt(table.indexOf(node.getNodeId()));
                out.writeDouble(node.getLon());
                out.writeDouble(node.getLat());
            }

            // Adjacency
            Map<String, List<Edge>> adj = graph.getOutgoingAdj();
            out.writeInt(adj.size());
            for (Map.Entry<String, List<Edge>> entry : adj.entrySet()) {
                out.writeInt(table.indexOf(entry.getKey()));
                List<Edge> edges = entry.getValue();
                out.writeInt(edges.size());
                for (Edge edge : edges) {
                    out.writeInt(table.indexOf(edge.getToNodeId()));
                    out.writeDouble(edge.getSegmentDistanceMeters());
                    writeNullableString(out, table, edge.getHighway());
                    writeNullableString(out, table, edge.getMaxspeedRaw());
                    writeNullableString(out, table, edge.getOnewayRaw());
                    writeNullableString(out, table, edge.getRoadName());
                    writeRawTags(out, table, edge.getRawTags());
                }
            }

            // Search items
            out.writeInt(items.size());
            for (SearchItem item : items) {
                out.writeInt(item.id());
                writeNullableString(out, table, item.name());
                writeNullableString(out, table, item.displayName());
                writeNullableString(out, table, item.type());
                writeNullableString(out, table, item.subType());
                out.writeDouble(item.lat());
                out.writeDouble(item.lon());
                writeNullableString(out, table, item.source());
                out.writeBoolean(item.routable());

                Map<String, String> metadata = item.metadata() == null ? Map.of() : item.metadata();
                out.writeInt(metadata.size());
                for (Map.Entry<String, String> e : metadata.entrySet()) {
                    out.writeInt(table.indexOf(e.getKey()));
                    out.writeInt(table.indexOf(e.getValue()));
                }

                List<String> tokens = item.searchTokens() == null ? List.of() : item.searchTokens();
                out.writeInt(tokens.size());
                for (String token : tokens) {
                    out.writeInt(table.indexOf(token));
                }

                List<String> studentTags = item.studentTags() == null ? List.of() : item.studentTags();
                out.writeInt(studentTags.size());
                for (String tag : studentTags) {
                    out.writeInt(table.indexOf(tag));
                }
            }

            // Report counters
            out.writeInt(report.getFeaturesSeen());
            out.writeInt(report.getLineStringRoadFeatures());
            out.writeInt(report.getSkippedFeatures());
            out.writeInt(report.getSegmentsBuilt());

            out.flush();
        } catch (IOException e) {
            log.warn("Failed to write graph cache; leaving existing cache untouched", e);
            try { Files.deleteIfExists(tempPath); } catch (IOException ignore) {}
            return;
        }

        try {
            Files.move(tempPath, cachePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFail) {
            try {
                Files.move(tempPath, cachePath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.warn("Failed to publish graph cache to {}", cachePath, e);
                try { Files.deleteIfExists(tempPath); } catch (IOException ignore) {}
                return;
            }
        }

        long durationMs = (System.nanoTime() - t0) / 1_000_000L;
        long bytes;
        try { bytes = Files.size(cachePath); } catch (IOException e) { bytes = -1; }
        log.info("Graph cache written to {} in {} ms ({} bytes, {} unique strings)",
                cachePath, durationMs, bytes, table.size());
    }

    private static void collectStrings(Graph graph, List<SearchItem> items, StringTable table) {
        for (Node node : graph.getNodesById().values()) {
            table.add(node.getNodeId());
        }
        for (Map.Entry<String, List<Edge>> entry : graph.getOutgoingAdj().entrySet()) {
            table.add(entry.getKey());
            for (Edge edge : entry.getValue()) {
                table.add(edge.getToNodeId());
                table.add(edge.getHighway());
                table.add(edge.getMaxspeedRaw());
                table.add(edge.getOnewayRaw());
                table.add(edge.getRoadName());
                for (Map.Entry<String, Object> t : edge.getRawTags().entrySet()) {
                    table.add(t.getKey());
                    Object v = t.getValue();
                    if (v instanceof String s) table.add(s);
                }
            }
        }
        for (SearchItem item : items) {
            table.add(item.name());
            table.add(item.displayName());
            table.add(item.type());
            table.add(item.subType());
            table.add(item.source());
            if (item.metadata() != null) {
                for (Map.Entry<String, String> e : item.metadata().entrySet()) {
                    table.add(e.getKey());
                    table.add(e.getValue());
                }
            }
            if (item.searchTokens() != null) {
                for (String s : item.searchTokens()) table.add(s);
            }
            if (item.studentTags() != null) {
                for (String s : item.studentTags()) table.add(s);
            }
        }
    }

    private static void writeNullableString(DataOutputStream out, StringTable table, String value) throws IOException {
        out.writeInt(value == null ? -1 : table.indexOf(value));
    }

    private static String readNullableString(DataInputStream in, String[] strings) throws IOException {
        int idx = in.readInt();
        return idx < 0 ? null : strings[idx];
    }

    private static void writeRawTags(DataOutputStream out, StringTable table, Map<String, Object> rawTags) throws IOException {
        if (rawTags == null || rawTags.isEmpty()) {
            out.writeInt(0);
            return;
        }
        out.writeInt(rawTags.size());
        for (Map.Entry<String, Object> entry : rawTags.entrySet()) {
            out.writeInt(table.indexOf(entry.getKey()));
            Object v = entry.getValue();
            if (v instanceof String s) {
                out.writeByte(TAG_STRING);
                out.writeInt(table.indexOf(s));
            } else if (v instanceof Integer i) {
                out.writeByte(TAG_INT);
                out.writeInt(i);
            } else if (v instanceof Long l) {
                out.writeByte(TAG_LONG);
                out.writeLong(l);
            } else if (v instanceof Double d) {
                out.writeByte(TAG_DOUBLE);
                out.writeDouble(d);
            } else if (v instanceof Float f) {
                out.writeByte(TAG_DOUBLE);
                out.writeDouble(f.doubleValue());
            } else if (v instanceof Boolean b) {
                out.writeByte(TAG_BOOL);
                out.writeBoolean(b);
            } else {
                // Fallback: coerce to String so we never silently drop data.
                out.writeByte(TAG_STRING);
                out.writeInt(table.indexOf(v.toString()));
            }
        }
    }

    private static Map<String, Object> readRawTags(DataInputStream in, String[] strings) throws IOException {
        int count = in.readInt();
        if (count == 0) {
            return Map.of();
        }
        Map<String, Object> map = new HashMap<>(count * 2);
        for (int i = 0; i < count; i++) {
            String key = strings[in.readInt()];
            byte type = in.readByte();
            Object value = switch (type) {
                case TAG_STRING -> strings[in.readInt()];
                case TAG_INT -> in.readInt();
                case TAG_LONG -> in.readLong();
                case TAG_DOUBLE -> in.readDouble();
                case TAG_BOOL -> in.readBoolean();
                default -> throw new IOException("Unknown rawTag type code: " + type);
            };
            map.put(key, value);
        }
        return Collections.unmodifiableMap(map);
    }

    /** Deduplicating string table indexed by insertion order. */
    private static final class StringTable {
        private final Map<String, Integer> index = new HashMap<>();
        private final List<String> ordered = new ArrayList<>();

        int add(String s) {
            if (s == null) return -1;
            Integer existing = index.get(s);
            if (existing != null) return existing;
            int next = ordered.size();
            index.put(s, next);
            ordered.add(s);
            return next;
        }

        int indexOf(String s) {
            Integer existing = index.get(s);
            if (existing == null) {
                // Should not happen if collectStrings covered every string, but fail loudly
                throw new IllegalStateException("String missing from table: " + s);
            }
            return existing;
        }

        int size() {
            return ordered.size();
        }

        List<String> ordered() {
            return ordered;
        }
    }

    static final class Bundle {
        private final Graph graph;
        private final List<SearchItem> items;
        private final GraphBuildReport report;

        Bundle(Graph graph, List<SearchItem> items, GraphBuildReport report) {
            this.graph = graph;
            this.items = items;
            this.report = report;
        }

        Graph graph() { return graph; }
        List<SearchItem> items() { return items; }
        GraphBuildReport report() { return report; }
    }
}
