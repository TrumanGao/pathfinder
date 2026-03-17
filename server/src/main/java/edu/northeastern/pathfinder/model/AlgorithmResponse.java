package edu.northeastern.pathfinder.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Frontend response payload for route/pathfinding.
 */
public class AlgorithmResponse {
    private String algorithm;
    private boolean pathFound;
    private double totalDistanceMeters;
    private long runtimeMs;

    private String startNodeId;
    private String endNodeId;

    private List<Node> path = new ArrayList<>();

    public AlgorithmResponse() {
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public boolean isPathFound() {
        return pathFound;
    }

    public void setPathFound(boolean pathFound) {
        this.pathFound = pathFound;
    }

    public double getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    public void setTotalDistanceMeters(double totalDistanceMeters) {
        this.totalDistanceMeters = totalDistanceMeters;
    }

    public long getRuntimeMs() {
        return runtimeMs;
    }

    public void setRuntimeMs(long runtimeMs) {
        this.runtimeMs = runtimeMs;
    }

    public String getStartNodeId() {
        return startNodeId;
    }

    public void setStartNodeId(String startNodeId) {
        this.startNodeId = startNodeId;
    }

    public String getEndNodeId() {
        return endNodeId;
    }

    public void setEndNodeId(String endNodeId) {
        this.endNodeId = endNodeId;
    }

    public List<Node> getPath() {
        return path;
    }

    public void setPath(List<Node> path) {
        this.path = path;
    }
}
