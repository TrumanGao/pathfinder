package edu.northeastern.pathfinder.dto;

/**
 * EN: Request DTO for the route endpoint.
 * Each endpoint may be specified either by graph nodeId or by coordinates. This phase supports
 * three real objectives only: distance, time, and balanced. Weighted routing beyond these fields
 * is intentionally not implemented yet.
 * 中文：路由接口的请求 DTO。
 * 起点和终点都可以通过图节点 nodeId 或坐标指定。本阶段只支持三种真实目标：
 * distance、time、balanced。除此之外更复杂的加权路由尚未实现。
 */
public record RouteRequestDto(
        RouteLocationInputDto start,
        RouteLocationInputDto end,
        String algorithm,
        String objective,
        RouteWeightsDto weights,
        RoadPreferencesDto roadPreferences
) {
    /**
     * EN: Route endpoint input.
     * The caller may provide nodeId directly, or lat/lon for nearest-node snapping.
     * 中文：路由端点输入。
     * 调用方可以直接提供 nodeId，或提供 lat/lon 让后端执行最近节点吸附。
     */
    public record RouteLocationInputDto(
            String nodeId,
            Double lat,
            Double lon
    ) {
    }

    /**
     * EN: Optional balanced-objective weights.
     * They are only used for the balanced objective; distance and time ignore them.
     * 中文：可选的 balanced 目标权重。
     * 这些权重只在 balanced 目标下生效；distance 和 time 会忽略它们。
     */
    public record RouteWeightsDto(
            Double distanceWeight,
            Double timeWeight
    ) {
    }

    /**
     * EN: First small set of supported road preferences.
     * They act as simple cost multipliers and do not implement a full preference engine.
     * 中文：首批受支持的道路偏好。
     * 它们通过简单成本倍率生效，并不是完整的偏好引擎。
     */
    public record RoadPreferencesDto(
            Boolean avoidHighway,
            Boolean preferMainRoad
    ) {
    }
}
