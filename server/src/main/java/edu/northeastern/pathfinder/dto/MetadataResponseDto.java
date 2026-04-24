package edu.northeastern.pathfinder.dto;

import java.util.List;

/**
 * Capability summary returned by /api/metadata.
 */
public record MetadataResponseDto(
        List<String> algorithms,
        String defaultAlgorithm,
        SearchMetadataDto search,
        RoutingMetadataDto routing,
        DatasetMetadataDto dataset,
        MapBoundsDto bounds
) {
    public record SearchMetadataDto(
            List<String> supportedTypes,
            int defaultLimit,
            int maxLimit
    ) {
    }

    public record RoutingMetadataDto(
            List<String> supportedObjectives,
            String defaultObjective,
            DefaultWeightsDto defaultWeights,
            List<String> supportedRoadPreferences,
            String defaultAlgorithm
    ) {
    }

    public record DefaultWeightsDto(
            double distanceWeight,
            double timeWeight
    ) {
    }

    public record DatasetMetadataDto(
            int nodeCount,
            int edgeCount
    ) {
    }

    public record MapBoundsDto(
            double minLat,
            double maxLat,
            double minLon,
            double maxLon
    ) {
    }
}
