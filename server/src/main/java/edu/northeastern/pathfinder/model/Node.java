package edu.northeastern.pathfinder.model;

/**
 * Response path node for frontend rendering.
 */
public class Node {
    private int nodeId;
    private double lat;
    private double lon;

    public Node() {
    }

    public Node(int nodeId, double lat, double lon) {
        this.nodeId = nodeId;
        this.lat = lat;
        this.lon = lon;
    }

    public int getNodeId() {
        return nodeId;
    }

    public void setNodeId(int nodeId) {
        this.nodeId = nodeId;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }
}
