package edu.northeastern.pathfinder.controller;

import edu.northeastern.pathfinder.dto.NearestResponseDto;
import edu.northeastern.pathfinder.service.NearestNodeService;
import edu.northeastern.pathfinder.service.RoutingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Snaps an input coordinate to its nearest routable graph node.
 */
@RestController
@RequestMapping("/api")
public class NearestController {
    private final NearestNodeService nearestNodeService;
    private final RoutingService routingService;

    public NearestController(NearestNodeService nearestNodeService, RoutingService routingService) {
        this.nearestNodeService = nearestNodeService;
        this.routingService = routingService;
    }

    @GetMapping("/nearest")
    public NearestResponseDto nearest(
            @RequestParam("lat") double lat,
            @RequestParam("lon") double lon
    ) {
        NearestNodeService.NearestNodeMatch nearest = nearestNodeService.findNearestNode(lat, lon);
        RoutingService.NodeReference node = routingService.getNodeReferenceByGraphNodeId(nearest.nodeId());

        return new NearestResponseDto(
                true,
                new NearestResponseDto.InputCoordinateDto(lat, lon),
                node.nodeId(),
                node.lat(),
                node.lon(),
                nearest.distanceMeters()
        );
    }
}
