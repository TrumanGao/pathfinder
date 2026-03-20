package edu.northeastern.pathfinder.controller;

import edu.northeastern.pathfinder.dto.MetadataResponseDto;
import edu.northeastern.pathfinder.service.RoutingOptions;
import edu.northeastern.pathfinder.service.RoutingService;
import edu.northeastern.pathfinder.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * EN: Metadata API controller describing the backend capabilities that are actually implemented today.
 * It helps the frontend discover supported algorithms, search limits, route objectives,
 * and current road-preference support.
 * 中文：用于描述当前后端真实已实现能力的元数据接口控制器。
 * 它帮助前端了解支持的算法、搜索限制、路由目标，以及当前道路偏好支持情况。
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

    /**
     * EN: Returns a lightweight capabilities summary for the current backend.
     * Only currently implemented objectives and road preferences are exposed.
     * 中文：返回当前后端能力的轻量级摘要。
     * 这里只暴露已经真实实现的目标和道路偏好。
     */
    @GetMapping("/metadata")
    public MetadataResponseDto metadata() {
        RoutingOptions.BalancedWeights defaultWeights = routingService.getDefaultBalancedWeights();
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
                )
        );
    }
}
