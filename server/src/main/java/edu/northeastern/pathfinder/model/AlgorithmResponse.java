package edu.northeastern.pathfinder.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Route response contract used by the frontend.
 */
public class AlgorithmResponse {
    private List<AlgorithmResult> results = new ArrayList<>();

    public List<AlgorithmResult> getResults() {
        return results;
    }

    public void setResults(List<AlgorithmResult> results) {
        this.results = results == null ? new ArrayList<>() : results;
    }

    public static class AlgorithmResult {
        private String algorithm;
        private List<Node> path = new ArrayList<>();
        private List<Node> visitedOrder = new ArrayList<>();
        private double distanceM;
        private long durationMs;
        private int visitedCount;

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public List<Node> getPath() {
            return path;
        }

        public void setPath(List<Node> path) {
            this.path = path == null ? new ArrayList<>() : path;
        }

        public List<Node> getVisitedOrder() {
            return visitedOrder;
        }

        public void setVisitedOrder(List<Node> visitedOrder) {
            this.visitedOrder = visitedOrder == null ? new ArrayList<>() : visitedOrder;
        }

        public double getDistanceM() {
            return distanceM;
        }

        public void setDistanceM(double distanceM) {
            this.distanceM = distanceM;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }

        public int getVisitedCount() {
            return visitedCount;
        }

        public void setVisitedCount(int visitedCount) {
            this.visitedCount = visitedCount;
        }
    }
}
