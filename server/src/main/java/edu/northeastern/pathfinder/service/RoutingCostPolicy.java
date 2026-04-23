package edu.northeastern.pathfinder.service;

import edu.northeastern.pathfinder.config.RoutingProperties;
import edu.northeastern.pathfinder.graph.Edge;
import edu.northeastern.pathfinder.graph.Node;
import edu.northeastern.pathfinder.pathfinding.PathCostModel;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Builds a PathCostModel from per-request RoutingOptions.
 * Supports distance / time / balanced objectives and road-preference multipliers.
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
     * Balanced blend, unified in seconds:
     * balanced = distanceWeight * (distance / referenceSpeed) + timeWeight * actualTime
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

    /** Applies configurable multipliers for the avoidHighway / preferMainRoad flags. */
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
