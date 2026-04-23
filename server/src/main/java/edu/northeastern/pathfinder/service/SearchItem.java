package edu.northeastern.pathfinder.service;

import java.util.Map;

/** Internal normalized search record built from a GeoJSON feature. */
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
        Map<String, String> metadata
) {
}
