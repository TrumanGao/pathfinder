package edu.northeastern.pathfinder.service;

import java.util.Map;

/**
 * EN: Internal normalized search record built from GeoJSON features.
 * It keeps a small, stable set of fields for search and UI-facing summaries,
 * but it is not intended to be a full copy of raw OSM tags or a complete taxonomy model.
 * The type/subType pair is intentionally general so future datasets can add more categories
 * without redesigning the whole search pipeline.
 * 中文：从 GeoJSON 要素构建的内部标准化搜索记录。
 * 它保留一组小而稳定的字段，用于搜索和界面摘要展示，
 * 但并不打算成为原始 OSM 标签的完整拷贝，也不是完整的分类体系。
 * 其中 type/subType 故意保持通用，便于未来数据集扩展更多类别而无需重做整体搜索流程。
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
        Map<String, String> metadata
) {
}
