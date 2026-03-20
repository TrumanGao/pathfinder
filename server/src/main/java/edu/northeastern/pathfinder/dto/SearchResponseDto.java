package edu.northeastern.pathfinder.dto;

import java.util.List;
import java.util.Map;

/**
 * EN: Response DTO for the current search endpoint.
 * It exposes normalized search results built from the internal SearchItem model,
 * but it does not expose raw GeoJSON features or full OSM tag payloads.
 * 中文：当前搜索接口的响应 DTO。
 * 它对外暴露基于内部 SearchItem 模型整理后的搜索结果，
 * 但不会暴露原始 GeoJSON 要素或完整的 OSM 标签载荷。
 */
public record SearchResponseDto(
        String query,
        int count,
        List<SearchResultDto> results
) {
    /**
     * EN: One normalized search result returned to the frontend.
     * The metadata field is intentionally small and useful rather than exhaustive.
     * 中文：返回给前端的单条标准化搜索结果。
     * 其中 metadata 刻意保持小而有用，而不是穷举所有原始标签。
     */
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
            Map<String, String> metadata
    ) {
    }
}
