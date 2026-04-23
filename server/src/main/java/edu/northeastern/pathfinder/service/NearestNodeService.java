package edu.northeastern.pathfinder.service;

import edu.northeastern.pathfinder.graph.Graph;
import edu.northeastern.pathfinder.graph.Node;
import org.springframework.stereotype.Service;

/**
 * Resolves coordinates to the nearest graph node via a linear scan.
 */
@Service
public class NearestNodeService {
    private final Graph graph;

    public NearestNodeService(Graph graph) {
        this.graph = graph;
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
