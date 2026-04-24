package edu.northeastern.pathfinder.benchmark;

import edu.northeastern.pathfinder.config.SearchProperties;
import edu.northeastern.pathfinder.graph.GeoJsonLoader;
import edu.northeastern.pathfinder.service.SearchItem;
import edu.northeastern.pathfinder.service.SearchService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Diagnoses why a specific query term fails to return expected results.
 *
 * Workflow (no heavy I/O — uses the existing graph cache):
 * 1. Load SearchItems via the normal loader (cache-hit path).
 * 2. Dump every item whose raw name OR searchTokens contains the target
 *    substring, so we can see what survived the classify/filter pipeline.
 * 3. Separately, list items whose metadata name contains the substring
 *    but whose searchTokens do NOT — those are tokenization bugs.
 * 4. Report the items that were rejected at classify() time by scanning
 *    for the same substring in the geojson is out of scope; the
 *    surviving-vs-tokenized comparison is usually enough to localise.
 *
 * Run:
 *   ./mvnw.cmd org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
 *       -Dexec.classpathScope=test \
 *       -Dexec.mainClass=edu.northeastern.pathfinder.benchmark.SearchDiagnosticTool \
 *       -Dexec.args="pho"
 */
public final class SearchDiagnosticTool {

    private SearchDiagnosticTool() {}

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: SearchDiagnosticTool <query>   (or) --arlington-suite");
            System.exit(1);
        }

        GeoJsonLoader loader = new GeoJsonLoader(
                "../data/full.geojson",
                "../data/graph-cache.bin",
                false
        );

        if ("--arlington-suite".equals(args[0])) {
            runArlingtonSuite(loader);
            return;
        }

        String needle = String.join(" ", args).toLowerCase(Locale.ROOT);

        List<SearchItem> items = loader.getSearchItems();
        System.out.printf("Loaded %d search items from cache/geojson%n%n", items.size());

        List<SearchItem> nameContains = new ArrayList<>();
        List<SearchItem> tokensContain = new ArrayList<>();
        List<SearchItem> nameButNoToken = new ArrayList<>();
        Map<String, Integer> tokenFreq = new TreeMap<>();

        for (SearchItem item : items) {
            String name = item.name() == null ? "" : item.name().toLowerCase(Locale.ROOT);
            boolean inName = name.contains(needle);
            boolean inTokens = item.searchTokens() != null && item.searchTokens().contains(needle);

            if (inName) nameContains.add(item);
            if (inTokens) tokensContain.add(item);
            if (inName && !inTokens) nameButNoToken.add(item);

            if (item.searchTokens() != null) {
                for (String t : item.searchTokens()) {
                    if (t.equals(needle) || t.startsWith(needle) || t.contains(needle)) {
                        tokenFreq.merge(t, 1, Integer::sum);
                    }
                }
            }
        }

        System.out.printf("Items with \"%s\" in name: %d%n", needle, nameContains.size());
        System.out.printf("Items with \"%s\" in searchTokens: %d%n", needle, tokensContain.size());
        System.out.printf("Items with name-match but NOT token-match (tokenization bug): %d%n%n",
                nameButNoToken.size());

        System.out.printf("--- Distinct indexed tokens matching \"%s\" (top 30) ---%n", needle);
        tokenFreq.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(30)
                .forEach(e -> System.out.printf("  %-30s  %d items%n", e.getKey(), e.getValue()));
        System.out.println();

        int showLimit = 25;
        System.out.printf("--- First %d items with \"%s\" in name ---%n", showLimit, needle);
        int shown = 0;
        for (SearchItem item : nameContains) {
            if (shown++ >= showLimit) break;
            String firstTokens = item.searchTokens() == null ? "[]" :
                    item.searchTokens().stream().limit(8).toList().toString();
            System.out.printf("  id=%d name=%s type=%s subType=%s lat=%.5f lon=%.5f tokens=%s%n",
                    item.id(), item.name(), item.type(), item.subType(),
                    item.lat(), item.lon(), firstTokens);
        }

        if (!nameButNoToken.isEmpty() && nameButNoToken.size() < 30) {
            System.out.println();
            System.out.println("!!! Items whose name contains the substring but tokens do not !!!");
            for (SearchItem item : nameButNoToken) {
                System.out.printf("  id=%d name=%s tokens=%s%n",
                        item.id(), item.name(), item.searchTokens());
            }
        }

        // Exercise the real SearchService path end-to-end
        System.out.println();
        System.out.println("--- SearchService.search(needle) end-to-end ---");
        SearchProperties props = new SearchProperties();
        props.setDefaultLimit(10);
        props.setMaxLimit(50);
        SearchService service = new SearchService(props, loader);

        // Replicate exactly what SearchController does with empty types/tags and default limit.
        List<SearchItem> hits = service.search(needle, List.of(), List.of(), 10);
        System.out.printf("%nservice.search(\"%s\", types=[], tags=[], limit=10) returned %d hits%n",
                needle, hits.size());
        int rank = 0;
        for (SearchItem item : hits) {
            rank++;
            boolean isTarget = item.name() != null && item.name().toLowerCase(Locale.ROOT).contains("pho 75");
            System.out.printf("  %2d. id=%d name=%s type=%s%s%n",
                    rank, item.id(), item.name(), item.type(),
                    isTarget ? "  <-- TARGET" : "");
        }
    }

    /**
     * Sanity check against a panel of well-known Arlington, VA landmarks.
     * Prints a concise PASS/FAIL table — used to confirm the full data flow
     * (GeoJSON → cache → SearchItem → inverted index → search) is intact.
     */
    private static void runArlingtonSuite(GeoJsonLoader loader) {
        SearchProperties props = new SearchProperties();
        props.setDefaultLimit(10);
        props.setMaxLimit(50);
        SearchService service = new SearchService(props, loader);

        // Each probe: (query, expected substring somewhere in top-10 name)
        String[][] probes = {
                {"Pho 75", "pho 75"},
                {"Pentagon", "pentagon"},
                {"Rosslyn", "rosslyn"},
                {"Ballston", "ballston"},
                {"Clarendon", "clarendon"},
                {"Courthouse", "courthouse"},
                {"Crystal City", "crystal city"},
                {"Arlington Cemetery", "arlington"},
                {"Columbia Pike", "columbia"},
                {"Wilson Boulevard", "wilson"},
                {"Pho", "pho"},
        };

        System.out.println();
        System.out.printf("%-28s %-8s %-6s %s%n", "Query", "Result", "Rank", "Top hit");
        System.out.println("-".repeat(100));
        int passes = 0;
        for (String[] probe : probes) {
            String query = probe[0];
            String expectedSubstr = probe[1].toLowerCase(Locale.ROOT);

            List<SearchItem> hits = service.search(query, List.of(), List.of(), 10);
            String topHit = hits.isEmpty() ? "(none)" : hits.get(0).name();

            int rank = -1;
            for (int i = 0; i < hits.size(); i++) {
                String name = hits.get(i).name();
                if (name != null && name.toLowerCase(Locale.ROOT).contains(expectedSubstr)) {
                    rank = i + 1;
                    break;
                }
            }
            String status = rank > 0 ? "PASS" : "FAIL";
            if (rank > 0) passes++;
            System.out.printf("%-28s %-8s %-6s %s%n",
                    query, status, rank > 0 ? String.valueOf(rank) : "-", topHit);
        }
        System.out.println("-".repeat(100));
        System.out.printf("%d / %d probes hit the expected result in top 10%n", passes, probes.length);
    }
}
