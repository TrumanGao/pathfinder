package edu.northeastern.pathfinder.model;

/**
 * Frontend request payload for route/pathfinding.
 */
public class AlgorithmRequest {
    private double startLat;
    private double startLon;
    private double endLat;
    private double endLon;

    /** Optional: "astar" (default) or "dijkstra" */
    private String algorithm;

    public AlgorithmRequest() {
    }

    public double getStartLat() {
        return startLat;
    }

    public void setStartLat(double startLat) {
        this.startLat = startLat;
    }

    public double getStartLon() {
        return startLon;
    }

    public void setStartLon(double startLon) {
        this.startLon = startLon;
    }

    /** Alias support for contracts using lng instead of lon. */
    public double getStartLng() {
        return startLon;
    }

    public void setStartLng(double startLng) {
        this.startLon = startLng;
    }

    public double getEndLat() {
        return endLat;
    }

    public void setEndLat(double endLat) {
        this.endLat = endLat;
    }

    public double getEndLon() {
        return endLon;
    }

    public void setEndLon(double endLon) {
        this.endLon = endLon;
    }

    /** Alias support for contracts using lng instead of lon. */
    public double getEndLng() {
        return endLon;
    }

    public void setEndLng(double endLng) {
        this.endLon = endLng;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
}
