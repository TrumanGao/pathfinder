package edu.northeastern.pathfinder.dto;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * EN: Response DTO for search and nearby-search endpoints.
 * 中文：搜索和附近搜索端点的响应 DTO。
 */
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
