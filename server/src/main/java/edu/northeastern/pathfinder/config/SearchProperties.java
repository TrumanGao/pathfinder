package edu.northeastern.pathfinder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * EN: Minimal defaults for GeoJSON-backed search.
 * This does not add fuzzy matching, ranking models, or external search infrastructure.
 * 中文：基于 GeoJSON 搜索的最小默认配置。
 * 该配置不包含模糊匹配、复杂排序或外部搜索基础设施。
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
