package edu.northeastern.pathfinder.service;

import edu.northeastern.pathfinder.config.RoutingProperties;
import edu.northeastern.pathfinder.graph.Edge;
import edu.northeastern.pathfinder.graph.Node;
import edu.northeastern.pathfinder.pathfinding.PathCostModel;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * EN: Computes effective edge cost for distance, time, and balanced routing objectives.
 * The policy is request-scoped through RoutingOptions, so different requests can use different
 * objectives and preferences without mutating graph weights. The implementation is intentionally
 * simple: small speed parsing, configurable defaults, and only two road preferences.
 * 中文：为 distance、time、balanced 路由目标计算有效边成本。
 * 该策略通过 RoutingOptions 保持在单次请求范围内，因此不同请求可以使用不同目标和偏好，
 * 而无需修改图上的全局权重。实现刻意保持简单：少量速度解析、可配置默认值，以及两个道路偏好。
 */
@Component
public class RoutingCostPolicy {
    private final RoutingProperties properties;
    private final SpeedResolver speedResolver;

    public RoutingCostPolicy(RoutingProperties properties, SpeedResolver speedResolver) {
        this.properties = properties;
        this.speedResolver = speedResolver;
    }

    public PathCostModel create(RoutingOptions options) {
        return new PathCostModel() {
            @Override
            public double edgeCost(Edge edge) {
                double baseCost = switch (options.objective()) {
                    case DISTANCE -> edge.getSegmentDistanceMeters();
                    case TIME -> actualTimeSeconds(edge);
                    case BALANCED -> balancedCostSeconds(edge, options.weights());
                };
                return baseCost * roadPreferenceMultiplier(edge, options.roadPreferences());
            }

            @Override
            public double heuristicCost(Node from, Node to) {
                double straightLineMeters = PathCostModel.haversineMeters(from, to);
                return switch (options.objective()) {
                    case DISTANCE -> straightLineMeters;
                    case TIME -> straightLineMeters / maxReasonableMetersPerSecond();
                    case BALANCED -> balancedHeuristicSeconds(straightLineMeters, options.weights());
                };
            }
        };
    }

    /**
     * EN: Balanced objective uses a time-equivalent blend so both terms stay in the same unit.
     * Formula:
     * balanced = distanceWeight * (distance / referenceSpeed)
     *          + timeWeight * actualTime
     * This is intentionally simple and does not yet model junction delay or traffic.
     * 中文：balanced 目标使用时间等价的混合方式，保证两项都处于同一单位。
     * 公式：
     * balanced = distanceWeight * (distance / referenceSpeed)
     *          + timeWeight * actualTime
     * 本阶段刻意保持简单，不建模路口延迟或交通流量。
     */
    private double balancedCostSeconds(Edge edge, RoutingOptions.BalancedWeights weights) {
        double distance = edge.getSegmentDistanceMeters();
        double actualTime = actualTimeSeconds(edge);
        double distanceTimeEquivalent = distance / referenceMetersPerSecond();
        return (weights.distanceWeight() * distanceTimeEquivalent)
                + (weights.timeWeight() * actualTime);
    }

    private double balancedHeuristicSeconds(double straightLineMeters, RoutingOptions.BalancedWeights weights) {
        double distanceEquivalent = straightLineMeters / referenceMetersPerSecond();
        double conservativeTime = straightLineMeters / maxReasonableMetersPerSecond();
        return (weights.distanceWeight() * distanceEquivalent)
                + (weights.timeWeight() * conservativeTime);
    }

    private double actualTimeSeconds(Edge edge) {
        return edge.getSegmentDistanceMeters() / speedResolver.resolveMetersPerSecond(edge);
    }

    /**
     * EN: Road preferences are implemented as small configurable multipliers on top of the chosen
     * base objective cost. This phase only supports avoidHighway and preferMainRoad.
     * 中文：道路偏好通过作用在基础目标成本之上的小倍率实现。
     * 本阶段只支持 avoidHighway 和 preferMainRoad。
     */
    private double roadPreferenceMultiplier(Edge edge, RoutingOptions.RoadPreferences preferences) {
        String highway = normalize(edge.getHighway());
        if (highway == null) {
            return 1.0;
        }

        double multiplier = 1.0;
        if (preferences.avoidHighway()) {
            multiplier *= properties.getAvoidHighwayMultipliers().getOrDefault(highway, 1.0);
        }
        if (preferences.preferMainRoad()) {
            multiplier *= properties.getPreferMainRoadMultipliers().getOrDefault(highway, 1.0);
        }
        return multiplier;
    }

    private double referenceMetersPerSecond() {
        return Math.max(0.1, properties.getReferenceSpeedKph() / 3.6);
    }

    private double maxReasonableMetersPerSecond() {
        double maxKph = properties.getFallbackSpeedKph();
        for (Double speedKph : properties.getSpeedKphByHighway().values()) {
            if (speedKph != null && speedKph > maxKph) {
                maxKph = speedKph;
            }
        }
        return Math.max(0.1, maxKph / 3.6);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
