package edu.northeastern.pathfinder.controller;

import edu.northeastern.pathfinder.dto.MetadataResponseDto;
import edu.northeastern.pathfinder.service.RoutingOptions;
import edu.northeastern.pathfinder.service.RoutingService;
import edu.northeastern.pathfinder.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports backend capabilities: supported algorithms, search limits,
 * routing objectives, road preferences, and dataset stats.
 */
@RestController
@RequestMapping("/api")
public class MetadataController {
    private final RoutingService routingService;
    private final SearchService searchService;

    public MetadataController(RoutingService routingService, SearchService searchService) {
        this.routingService = routingService;
        this.searchService = searchService;
    }

    @GetMapping("/metadata")
    public MetadataResponseDto metadata() {
        RoutingOptions.BalancedWeights defaultWeights = routingService.getDefaultBalancedWeights();
        RoutingService.GraphBounds bounds = routingService.getBounds();
        return new MetadataResponseDto(
                routingService.getSupportedAlgorithms(),
                routingService.getDefaultAlgorithm(),
                new MetadataResponseDto.SearchMetadataDto(
                        searchService.getSupportedTypes(),
                        searchService.getDefaultLimit(),
                        searchService.getMaxLimit()
                ),
                new MetadataResponseDto.RoutingMetadataDto(
                        routingService.getSupportedObjectives(),
                        routingService.getDefaultObjective(),
                        new MetadataResponseDto.DefaultWeightsDto(
                                defaultWeights.distanceWeight(),
                                defaultWeights.timeWeight()
                        ),
                        routingService.getSupportedRoadPreferences(),
                        routingService.getDefaultAlgorithm()
                ),
                new MetadataResponseDto.DatasetMetadataDto(
                        routingService.getNodeCount(),
                        routingService.getEdgeCount()
                ),
                new MetadataResponseDto.MapBoundsDto(
                        bounds.minLat(),
                        bounds.maxLat(),
                        bounds.minLon(),
                        bounds.maxLon()
                )
        );
    }
}
