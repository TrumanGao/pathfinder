package edu.northeastern.pathfinder.service;

import java.util.Locale;

/** Supported routing objectives: distance, time, balanced. */
public enum RoutingObjective {
    DISTANCE("distance"),
    TIME("time"),
    BALANCED("balanced");

    private final String apiValue;

    RoutingObjective(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }

    public static RoutingObjective fromValue(String value, RoutingObjective fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (RoutingObjective objective : values()) {
            if (objective.apiValue.equals(normalized)) {
                return objective;
            }
        }
        return fallback;
    }
}
