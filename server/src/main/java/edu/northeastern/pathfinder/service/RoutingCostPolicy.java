package edu.northeastern.pathfinder.service;

import edu.northeastern.pathfinder.config.RoutingProperties;
import edu.northeastern.pathfinder.graph.Edge;
import edu.northeastern.pathfinder.graph.Node;
import edu.northeastern.pathfinder.pathfinding.PathCostModel;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

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
    /**
     * Minimum possible safety multiplier (best-case: lit + paved + footway).
     * Used in A* heuristic to stay admissible.
     */
    private static final double MIN_SAFETY_MULTIPLIER = 0.7 * 0.9 * 0.8;

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
                    case SAFE_WALK -> safeWalkCost(edge);
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
                    case SAFE_WALK -> straightLineMeters / maxReasonableMetersPerSecond() * MIN_SAFETY_MULTIPLIER;
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
     * EN: Safe walk cost = time-based cost × safety multiplier derived from edge tags.
     * The multiplier rewards lit, paved, pedestrian-friendly segments and penalizes
     * unlit, unpaved, high-speed, or restricted-access segments.
     * 中文：安全步行成本 = 时间成本 × 由边标签推导的安全乘数。
     * 乘数奖励有照明、铺装、步行友好的路段，惩罚无照明、未铺装、高速或限制通行路段。
     */
    private double safeWalkCost(Edge edge) {
        return actualTimeSeconds(edge) * safetyMultiplier(edge);
    }

    private double safetyMultiplier(Edge edge) {
        double multiplier = 1.0;
        Map<String, Object> tags = edge.getRawTags();

        // Lighting
        Object lit = tags.get("lit");
        if ("yes".equals(lit)) {
            multiplier *= 0.7;
        } else if ("no".equals(lit)) {
            multiplier *= 1.5;
        }

        // Surface quality
        Object surface = tags.get("surface");
        if (surface instanceof String s) {
            if (s.equals("asphalt") || s.equals("concrete") || s.equals("paved")) {
                multiplier *= 0.9;
            } else if (s.equals("gravel") || s.equals("dirt") || s.equals("unpaved") || s.equals("ground") || s.equals("mud")) {
                multiplier *= 1.4;
            }
        }

        // Highway type
        String highway = normalize(edge.getHighway());
        if (highway != null) {
            multiplier *= switch (highway) {
                case "footway", "pedestrian", "living_street", "path", "steps" -> 0.8;
                case "cycleway" -> 0.9;
                case "residential", "service" -> 1.0;
                case "tertiary", "unclassified" -> 1.1;
                case "secondary" -> 1.2;
                case "primary" -> 1.3;
                case "trunk" -> 2.5;
                case "motorway", "motorway_link", "trunk_link" -> 3.0;
                default -> 1.0;
            };
        }

        // Foot access
        Object foot = tags.get("foot");
        if ("no".equals(foot)) {
            multiplier *= 10.0;
        }

        // General access
        Object access = tags.get("access");
        if ("private".equals(access) || "no".equals(access)) {
            multiplier *= 5.0;
        }

        return multiplier;
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
