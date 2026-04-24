package edu.northeastern.pathfinder.dto;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Response payload for /api/search and /api/search/nearby. */
public record SearchResponseDto(
        String query,
        int count,
        List<SearchResultDto> results
) {
    public record SearchResultDto(
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
            Double distanceM,
            List<String> studentTags
    ) {
    }
}
