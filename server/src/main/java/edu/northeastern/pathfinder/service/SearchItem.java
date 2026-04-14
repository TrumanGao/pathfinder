package edu.northeastern.pathfinder.service;

import java.util.List;
import java.util.Map;

/**
 * EN: Internal normalized search record built from GeoJSON features.
 * searchTokens holds pre-computed lowercase tokens for efficient scored search.
 * studentTags holds lifestyle tags for international student filtering
 * (e.g. asian_food, halal, bubble_tea, grocery, pharmacy).
 * 中文：从 GeoJSON 要素构建的内部标准化搜索记录。
 * searchTokens 保存预计算的小写分词结果用于评分搜索。
 * studentTags 保存面向国际学生的生活标签（如 asian_food、halal、bubble_tea、grocery、pharmacy）。
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
