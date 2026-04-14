package edu.northeastern.pathfinder.service;

import edu.northeastern.pathfinder.config.NearestNodeProperties;
import edu.northeastern.pathfinder.graph.Graph;
import edu.northeastern.pathfinder.graph.Node;
import org.springframework.stereotype.Service;

/**
 * EN: Owns coordinate-to-nearest-graph-node lookup using a simple linear scan over the current graph.
 * It only resolves the nearest routable node and distance, and does not perform routing or search responsibilities.
 * 中文：负责“坐标 -> 最近图节点”的查询，当前通过对图节点进行简单线性扫描完成。
 * 该服务只负责返回最近的可路由节点及距离，不承担路由计算或搜索职责。
 */
@Service
public class NearestNodeService {
    private final Graph graph;
    private final NearestNodeProperties properties;

    public NearestNodeService(Graph graph, NearestNodeProperties properties) {
        this.graph = graph;
        this.properties = properties;
    }

    public NearestNodeMatch findNearestNode(double lat, double lon) {
        String bestGraphNodeId = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (Node node : graph.getNodesById().values()) {
            double d = haversineMeters(lat, lon, node.getLat(), node.getLon());
            if (d < bestDistance) {
                bestDistance = d;
                bestGraphNodeId = node.getNodeId();
            }
        }

        if (bestGraphNodeId == null) {
            throw new IllegalStateException("Graph has no nodes");
        }

        return new NearestNodeMatch(bestGraphNodeId, bestDistance);
    }

    public double getDefaultMaxDistanceMeters() {
        return properties.getMaxDistanceMeters();
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        return r * (2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a)));
    }

    public record NearestNodeMatch(String nodeId, double distanceMeters) {
    }
}
