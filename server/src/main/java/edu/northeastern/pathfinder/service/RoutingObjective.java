package edu.northeastern.pathfinder.service;

import java.util.Locale;

/**
 * EN: Supported routing objectives that are actually implemented in this phase.
 * The set is intentionally small: distance, time, and balanced.
 * 中文：本阶段真实实现的路由目标。
 * 目前刻意保持很小的范围：distance、time、balanced。
 */
public enum RoutingObjective {
    DISTANCE("distance"),
    TIME("time"),
    BALANCED("balanced"),
    SAFE_WALK("safe_walk");

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
