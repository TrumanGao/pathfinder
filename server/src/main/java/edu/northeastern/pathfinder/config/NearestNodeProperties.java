package edu.northeastern.pathfinder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * EN: Minimal defaults for nearest-node lookup.
 * This does not introduce spatial indexing or hard rejection by distance.
 * 中文：最近路网节点查询的最小默认配置。
 * 该配置不会引入空间索引，也不会按距离强制拒绝结果。
 */
@ConfigurationProperties(prefix = "pathfinder.nearest")
public class NearestNodeProperties {
    private double maxDistanceMeters = 1000.0;

    public double getMaxDistanceMeters() {
        return maxDistanceMeters;
    }

    public void setMaxDistanceMeters(double maxDistanceMeters) {
        this.maxDistanceMeters = maxDistanceMeters;
    }
}
