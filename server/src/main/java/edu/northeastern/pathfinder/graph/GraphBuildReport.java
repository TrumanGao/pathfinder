package edu.northeastern.pathfinder.graph;

/**
 * Minimal counters for graph-build diagnostics.
 */
public final class GraphBuildReport {
    private int featuresSeen;
    private int lineStringRoadFeatures;
    private int skippedFeatures;
    private int segmentsBuilt;

    public void incrementFeaturesSeen() {
        featuresSeen++;
    }

    public void incrementLineStringRoadFeatures() {
        lineStringRoadFeatures++;
    }

    public void incrementSkippedFeatures() {
        skippedFeatures++;
    }

    public void incrementSegmentsBuilt() {
        segmentsBuilt++;
    }

    public int getFeaturesSeen() {
        return featuresSeen;
    }

    public int getLineStringRoadFeatures() {
        return lineStringRoadFeatures;
    }

    public int getSkippedFeatures() {
        return skippedFeatures;
    }

    public int getSegmentsBuilt() {
        return segmentsBuilt;
    }
}
