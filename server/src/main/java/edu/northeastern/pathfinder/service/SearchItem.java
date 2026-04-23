package edu.northeastern.pathfinder.service;

import java.util.List;
import java.util.Map;

/**
 * Normalized search record built from a GeoJSON feature.
 * searchTokens: pre-computed lowercase tokens for scored search.
 * studentTags: lifestyle tags for international-student filtering
 * (e.g. asian_food, halal, bubble_tea, grocery, pharmacy).
 */
public record SearchItem(
        int id,
        String name,
        String displayName,
        String type,
        String subType,
        double lat,
        double lon,
        String source,
        boolean routable,
        Map<String, String> metadata,
        List<String> searchTokens,
        List<String> studentTags
) {
}
