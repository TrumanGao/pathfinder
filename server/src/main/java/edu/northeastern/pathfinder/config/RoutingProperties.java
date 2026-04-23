package edu.northeastern.pathfinder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tunable defaults for the edge-cost policy: objective, balanced weights,
 * per-highway speeds, and road-preference multipliers.
 */
@ConfigurationProperties(prefix = "pathfinder.routing")
public class RoutingProperties {
    private String defaultAlgorithm = "astar";
    private String defaultObjective = "distance";
    private BalancedWeights balanced = new BalancedWeights();
    private double referenceSpeedKph = 40.0;
    private double fallbackSpeedKph = 30.0;
    private Map<String, Double> speedKphByHighway = new LinkedHashMap<>();
    private Map<String, Double> avoidHighwayMultipliers = new LinkedHashMap<>();
    private Map<String, Double> preferMainRoadMultipliers = new LinkedHashMap<>();

    public String getDefaultAlgorithm() {
        return defaultAlgorithm;
    }

    public void setDefaultAlgorithm(String defaultAlgorithm) {
        this.defaultAlgorithm = defaultAlgorithm;
    }

    public String getDefaultObjective() {
        return defaultObjective;
    }

    public void setDefaultObjective(String defaultObjective) {
        this.defaultObjective = defaultObjective;
    }

    public BalancedWeights getBalanced() {
        return balanced;
    }

    public void setBalanced(BalancedWeights balanced) {
        this.balanced = balanced == null ? new BalancedWeights() : balanced;
    }

    public double getReferenceSpeedKph() {
        return referenceSpeedKph;
    }

    public void setReferenceSpeedKph(double referenceSpeedKph) {
        this.referenceSpeedKph = referenceSpeedKph;
    }

    public double getFallbackSpeedKph() {
        return fallbackSpeedKph;
    }

    public void setFallbackSpeedKph(double fallbackSpeedKph) {
        this.fallbackSpeedKph = fallbackSpeedKph;
    }

    public Map<String, Double> getSpeedKphByHighway() {
        return speedKphByHighway;
    }

    public void setSpeedKphByHighway(Map<String, Double> speedKphByHighway) {
        this.speedKphByHighway = speedKphByHighway == null ? new LinkedHashMap<>() : speedKphByHighway;
    }

    public Map<String, Double> getAvoidHighwayMultipliers() {
        return avoidHighwayMultipliers;
    }

    public void setAvoidHighwayMultipliers(Map<String, Double> avoidHighwayMultipliers) {
        this.avoidHighwayMultipliers = avoidHighwayMultipliers == null ? new LinkedHashMap<>() : avoidHighwayMultipliers;
    }

    public Map<String, Double> getPreferMainRoadMultipliers() {
        return preferMainRoadMultipliers;
    }

    public void setPreferMainRoadMultipliers(Map<String, Double> preferMainRoadMultipliers) {
        this.preferMainRoadMultipliers = preferMainRoadMultipliers == null ? new LinkedHashMap<>() : preferMainRoadMultipliers;
    }

    public static class BalancedWeights {
        private double distanceWeight = 0.5;
        private double timeWeight = 0.5;

        public double getDistanceWeight() {
            return distanceWeight;
        }

        public void setDistanceWeight(double distanceWeight) {
            this.distanceWeight = distanceWeight;
        }

        public double getTimeWeight() {
            return timeWeight;
        }

        public void setTimeWeight(double timeWeight) {
            this.timeWeight = timeWeight;
        }
    }
}
