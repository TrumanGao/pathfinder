package edu.northeastern.pathfinder.dto;

import java.util.List;

/**
 * Route response carrying the algorithm used, resolved endpoints, and the path.
 */
public record RouteResponseDto(
        boolean success,
        String algorithm,
        String objective,
        ResolvedRouteLocationDto start,
        ResolvedRouteLocationDto end,
        List<RoutePathNodeDto> path,
        Double distanceM,
        Double estimatedTimeSeconds,
        int pathNodeCount
) {
    public record ResolvedRouteLocationDto(
            RouteRequestDto.RouteLocationInputDto input,
            String resolvedNodeId,
            double lat,
            double lon,
            double snapDistanceM
    ) {
    }

    public record RoutePathNodeDto(
            String nodeId,
            double lat,
            double lon
    ) {
    }
}
