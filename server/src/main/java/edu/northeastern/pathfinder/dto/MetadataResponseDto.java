package edu.northeastern.pathfinder.dto;

import java.util.List;

/**
 * EN: Metadata DTO describing what the backend actually supports today.
 * It is intended for frontend capability discovery, not for configuration mutation.
 * 中文：用于描述当前后端真实支持能力的元数据 DTO。
 * 它主要服务于前端能力发现，而不是用于修改后端配置。
 */
public record MetadataResponseDto(
        List<String> algorithms,
        String defaultAlgorithm,
        SearchMetadataDto search,
        RoutingMetadataDto routing,
        DatasetMetadataDto dataset
) {
    /**
     * EN: Search capability metadata.
     * It reports the currently supported normalized types and simple search limits only.
     * 中文：搜索能力元数据。
     * 它只报告当前支持的标准化 type 以及简单的搜索限制。
     */
    public record SearchMetadataDto(
            List<String> supportedTypes,
            int defaultLimit,
            int maxLimit
    ) {
    }

    /**
     * EN: Routing capability metadata.
     * It exposes only real, currently implemented options: objectives, default weights,
     * supported road preferences, and the default algorithm.
     * 中文：路由能力元数据。
     * 这里仅暴露当前真实实现的选项：目标、默认权重、受支持的道路偏好，以及默认算法。
     */
    public record RoutingMetadataDto(
            List<String> supportedObjectives,
            String defaultObjective,
            DefaultWeightsDto defaultWeights,
            List<String> supportedRoadPreferences,
            String defaultAlgorithm
    ) {
    }

    /**
     * EN: Default weights for the balanced routing objective.
     * 中文：balanced 路由目标的默认权重。
     */
    public record DefaultWeightsDto(
            double distanceWeight,
            double timeWeight
    ) {
    }

    /**
     * EN: Lightweight dataset summary.
     * 中文：轻量级数据集摘要。
     */
    public record DatasetMetadataDto(
            int nodeCount,
            int edgeCount
    ) {
    }
}
