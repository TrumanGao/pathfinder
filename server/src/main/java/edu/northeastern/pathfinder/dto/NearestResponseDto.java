package edu.northeastern.pathfinder.dto;

/**
 * Response for coordinate-to-nearest-node snapping.
 */
public record NearestResponseDto(
        boolean matched,
        InputCoordinateDto input,
        String nodeId,
        double lat,
        double lon,
        double distanceM
) {
    public record InputCoordinateDto(double lat, double lon) {
    }
}
