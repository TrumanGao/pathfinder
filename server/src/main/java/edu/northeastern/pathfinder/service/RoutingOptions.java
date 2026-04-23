package edu.northeastern.pathfinder.service;

/** Per-request routing options: objective, balanced weights, road preferences. */
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
