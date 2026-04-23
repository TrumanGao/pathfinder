package edu.northeastern.pathfinder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Defaults for GeoJSON-backed search limits.
 */
@ConfigurationProperties(prefix = "pathfinder.search")
public class SearchProperties {
    private int defaultLimit = 10;
    private int maxLimit = 50;

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(int defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public int getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(int maxLimit) {
        this.maxLimit = maxLimit;
    }
}
