package edu.northeastern.pathfinder.service;

import edu.northeastern.pathfinder.config.SearchProperties;
import edu.northeastern.pathfinder.graph.GeoJsonLoader;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * EN: Provides scored keyword search and nearby search over pre-built SearchItem records.
 * Keyword search uses a pre-built inverted index with multi-level scoring:
 * exact token match, prefix match, contains match, and edit-distance fuzzy match.
 * Nearby search filters by type and sorts by haversine distance.
 * 中文：基于预构建 SearchItem 记录提供评分关键词搜索和附近搜索。
 * 关键词搜索使用预构建的倒排索引，支持多级评分：精确匹配、前缀匹配、包含匹配和编辑距离模糊匹配。
 * 附近搜索按类型过滤并按 haversine 距离排序。
 */
@Service
public class SearchService {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s/,;:()\\[\\]{}\"'\\-_.#@!?&|+*=<>]+");
    private static final Pattern ACCENT_STRIP = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private static final int SCORE_EXACT = 10;
    private static final int SCORE_PREFIX = 5;
    private static final int SCORE_CONTAINS = 2;
    private static final int SCORE_FUZZY = 3;
    private static final int MAX_EDIT_DISTANCE = 2;

    private final SearchProperties properties;
    private final List<SearchItem> items;

    /**
     * Inverted index: token → list of item indices in {@link #items}.
     */
    private final Map<String, List<Integer>> tokenIndex;

    public SearchService(SearchProperties properties, GeoJsonLoader loader) {
        this.properties = properties;
        this.items = Collections.unmodifiableList(new ArrayList<>(loader.getSearchItems()));
        this.tokenIndex = buildTokenIndex(this.items);
    }

    // ======================== Keyword Search ========================

    public List<SearchItem> search(String query, Integer limit) {
        return search(query, List.of(), List.of(), limit);
    }

    public List<SearchItem> search(String query, List<String> types, List<String> tags, Integer limit) {
        Set<String> normalizedTypes = normalizeTypes(types);
        Set<String> normalizedTags = normalizeTypes(tags);
        int boundedLimit = normalizeLimit(limit);
        List<String> queryTokens = tokenize(query);

        if (queryTokens.isEmpty()) {
            return items.stream()
                    .filter(item -> matchesFilters(item, normalizedTypes, normalizedTags))
                    .limit(boundedLimit)
                    .toList();
        }

        Map<Integer, Double> scores = new HashMap<>();
        Set<Integer> candidates = collectCandidates(queryTokens);

        for (int idx : candidates) {
            SearchItem item = items.get(idx);
            if (!matchesFilters(item, normalizedTypes, normalizedTags)) {
                continue;
            }
            double score = scoreItem(item, queryTokens);
            if (score > 0) {
                scores.put(idx, score);
            }
        }

        List<Map.Entry<Integer, Double>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Comparator.<Map.Entry<Integer, Double>, Double>comparing(Map.Entry::getValue).reversed()
                .thenComparing(e -> items.get(e.getKey()).name()));

        List<SearchItem> results = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : sorted) {
            results.add(items.get(entry.getKey()));
            if (results.size() >= boundedLimit) break;
        }
        return results;
    }

    // ======================== Nearby Search ========================

    /**
     * EN: Finds SearchItems within a radius of the given coordinates, filtered by type,
     * sorted by distance ascending.
     * 中文：在给定坐标的半径范围内查找 SearchItem，按类型过滤，按距离升序排序。
     */
    public List<NearbyResult> searchNearby(double lat, double lon, List<String> types, List<String> tags, double radiusMeters, Integer limit) {
        Set<String> normalizedTypes = normalizeTypes(types);
        Set<String> normalizedTags = normalizeTypes(tags);
        int boundedLimit = normalizeLimit(limit);

        List<NearbyResult> candidates = new ArrayList<>();
        for (SearchItem item : items) {
            if (!matchesFilters(item, normalizedTypes, normalizedTags)) {
                continue;
            }
            double distance = haversineMeters(lat, lon, item.lat(), item.lon());
            if (distance <= radiusMeters) {
                candidates.add(new NearbyResult(item, distance));
            }
        }

        candidates.sort(Comparator.comparingDouble(NearbyResult::distanceM));
        if (candidates.size() > boundedLimit) {
            return candidates.subList(0, boundedLimit);
        }
        return candidates;
    }

    public record NearbyResult(SearchItem item, double distanceM) {
    }

    // ======================== Public accessors ========================

    public int getDefaultLimit() {
        return properties.getDefaultLimit();
    }

    public int getMaxLimit() {
        return properties.getMaxLimit();
    }

    public List<String> getSupportedStudentTags() {
        return new ArrayList<>(new TreeSet<>(
                items.stream()
                        .flatMap(item -> item.studentTags().stream())
                        .toList()
        ));
    }

    public List<String> getSupportedTypes() {
        return new ArrayList<>(new TreeSet<>(
                items.stream().map(SearchItem::type).toList()
        ));
    }

    // ======================== Inverted index ========================

    private Map<String, List<Integer>> buildTokenIndex(List<SearchItem> items) {
        Map<String, List<Integer>> index = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            SearchItem item = items.get(i);
            Set<String> seen = new TreeSet<>();
            for (String token : item.searchTokens()) {
                if (seen.add(token)) {
                    index.computeIfAbsent(token, k -> new ArrayList<>()).add(i);
                }
            }
        }
        return index;
    }

    /**
     * Collect candidate item indices by finding all tokens in the index that
     * match any query token exactly, by prefix, or within edit distance.
     */
    private Set<Integer> collectCandidates(List<String> queryTokens) {
        Set<Integer> candidates = new TreeSet<>();
        for (String qt : queryTokens) {
            // Exact match
            List<Integer> exact = tokenIndex.get(qt);
            if (exact != null) {
                candidates.addAll(exact);
            }
            // Prefix and fuzzy: scan index keys (fast enough for ~100K distinct tokens)
            for (Map.Entry<String, List<Integer>> entry : tokenIndex.entrySet()) {
                String indexToken = entry.getKey();
                if (indexToken.startsWith(qt) || qt.startsWith(indexToken)) {
                    candidates.addAll(entry.getValue());
                } else if (Math.abs(indexToken.length() - qt.length()) <= MAX_EDIT_DISTANCE
                        && editDistance(qt, indexToken) <= MAX_EDIT_DISTANCE) {
                    candidates.addAll(entry.getValue());
                }
            }
        }
        return candidates;
    }

    // ======================== Scoring ========================

    private double scoreItem(SearchItem item, List<String> queryTokens) {
        double total = 0;
        List<String> nameTokens = tokenize(item.name());

        for (String qt : queryTokens) {
            double best = 0;
            for (String it : item.searchTokens()) {
                double s = scoreTokenPair(qt, it);
                // Name tokens get 2x weight
                if (nameTokens.contains(it)) {
                    s *= 2.0;
                }
                best = Math.max(best, s);
            }
            total += best;
        }
        return total;
    }

    private double scoreTokenPair(String queryToken, String itemToken) {
        if (queryToken.equals(itemToken)) return SCORE_EXACT;
        if (itemToken.startsWith(queryToken)) return SCORE_PREFIX;
        if (itemToken.contains(queryToken)) return SCORE_CONTAINS;
        if (Math.abs(queryToken.length() - itemToken.length()) <= MAX_EDIT_DISTANCE
                && editDistance(queryToken, itemToken) <= MAX_EDIT_DISTANCE) {
            return SCORE_FUZZY;
        }
        return 0;
    }

    // ======================== Edit distance ========================

    private int editDistance(String a, String b) {
        int m = a.length(), n = b.length();
        if (m == 0) return n;
        if (n == 0) return m;

        // Early termination: if length diff > max, skip
        if (Math.abs(m - n) > MAX_EDIT_DISTANCE) return MAX_EDIT_DISTANCE + 1;

        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        for (int j = 0; j <= n; j++) prev[j] = j;

        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            int rowMin = curr[0];
            for (int j = 1; j <= n; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, curr[j]);
            }
            // Early termination
            if (rowMin > MAX_EDIT_DISTANCE) return MAX_EDIT_DISTANCE + 1;
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[n];
    }

    // ======================== Tokenization ========================

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        String normalized = ACCENT_STRIP.matcher(
                Normalizer.normalize(text, Normalizer.Form.NFD)
        ).replaceAll("").toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        for (String token : TOKEN_SPLIT.split(normalized)) {
            if (!token.isEmpty()) tokens.add(token);
        }
        return tokens;
    }

    // ======================== Filtering ========================

    private boolean matchesFilters(SearchItem item, Set<String> types, Set<String> tags) {
        if (!types.isEmpty() && !types.contains(item.type())) {
            return false;
        }
        if (!tags.isEmpty()) {
            boolean hasAny = false;
            for (String tag : item.studentTags()) {
                if (tags.contains(tag)) {
                    hasAny = true;
                    break;
                }
            }
            if (!hasAny) return false;
        }
        return true;
    }

    // ======================== Helpers ========================

    private int normalizeLimit(Integer limit) {
        int resolved = limit == null ? properties.getDefaultLimit() : limit;
        return Math.max(1, Math.min(resolved, properties.getMaxLimit()));
    }

    private Set<String> normalizeTypes(List<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        TreeSet<String> normalized = new TreeSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
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
}
