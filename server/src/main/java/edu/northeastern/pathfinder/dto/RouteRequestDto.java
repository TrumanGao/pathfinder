package edu.northeastern.pathfinder.dto;

/**
 * Route request body. Each endpoint accepts either a nodeId or lat/lon.
 */
public record RouteRequestDto(
        RouteLocationInputDto start,
        RouteLocationInputDto end,
        String algorithm,
        String objective,
        RouteWeightsDto weights,
        RoadPreferencesDto roadPreferences
) {
    public record RouteLocationInputDto(
            String nodeId,
            Double lat,
            Double lon
    ) {
    }

    /** Weights used only for the balanced objective. */
    public record RouteWeightsDto(
            Double distanceWeight,
            Double timeWeight
    ) {
    }

    public record RoadPreferencesDto(
            Boolean avoidHighway,
            Boolean preferMainRoad
    ) {
    }
}
