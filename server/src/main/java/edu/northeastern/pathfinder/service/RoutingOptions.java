package edu.northeastern.pathfinder.service;

/**
 * EN: Request-scoped routing options parsed by RoutingService.
 * They describe the chosen objective, optional balanced weights, and the first small set of
 * road preferences. This is intentionally not a generic rule engine.
 * 中文：由 RoutingService 解析的“单次请求范围内”路由选项。
 * 这些选项描述了目标函数、可选的 balanced 权重，以及首批道路偏好。
 * 它并不是一个通用规则引擎。
 */
public record RoutingOptions(
        RoutingObjective objective,
        BalancedWeights weights,
        RoadPreferences roadPreferences
) {
    public record BalancedWeights(double distanceWeight, double timeWeight) {
    }

    public record RoadPreferences(boolean avoidHighway, boolean preferMainRoad) {
    }
}
