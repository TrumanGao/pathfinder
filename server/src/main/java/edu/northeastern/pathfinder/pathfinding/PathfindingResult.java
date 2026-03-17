package edu.northeastern.pathfinder.pathfinding;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Shared shortest-path output for current project stage.
 */
public final class PathfindingResult {
    private final boolean pathFound;
    private final double totalDistanceMeters;
    private final List<String> pathNodeIds;

    private PathfindingResult(boolean pathFound, double totalDistanceMeters, List<String> pathNodeIds) {
        this.pathFound = pathFound;
        this.totalDistanceMeters = totalDistanceMeters;
        this.pathNodeIds = Collections.unmodifiableList(Objects.requireNonNull(pathNodeIds));
    }

    public static PathfindingResult found(double totalDistanceMeters, List<String> pathNodeIds) {
        Objects.requireNonNull(pathNodeIds, "pathNodeIds must not be null");
        if (pathNodeIds.isEmpty()) {
            throw new IllegalArgumentException("found(...) requires a non-empty pathNodeIds list");
        }
        if (!Double.isFinite(totalDistanceMeters) || totalDistanceMeters < 0) {
            throw new IllegalArgumentException("found(...) requires a finite, non-negative distance");
        }
        return new PathfindingResult(true, totalDistanceMeters, pathNodeIds);
    }

    public static PathfindingResult notFound() {
        return new PathfindingResult(false, Double.POSITIVE_INFINITY, List.of());
    }

    public boolean isPathFound() {
        return pathFound;
    }

    public double getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    public List<String> getPathNodeIds() {
        return pathNodeIds;
    }
}
