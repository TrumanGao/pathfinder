package edu.northeastern.pathfinder.controller;

import edu.northeastern.pathfinder.dto.RouteRequestDto;
import edu.northeastern.pathfinder.dto.RouteResponseDto;
import edu.northeastern.pathfinder.service.RoutingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Computes a route between two endpoints (nodeId or coordinates).
 * Supports distance / time / balanced objectives.
 */
@RestController
@RequestMapping("/api")
public class RouteController {
    private final RoutingService routingService;

    public RouteController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @PostMapping("/route")
    public RouteResponseDto route(@RequestBody RouteRequestDto request) {
        validateRouteRequest(request);

        RoutingService.RouteComputation route = routingService.route(new RoutingService.RouteQuery(
                request.start().nodeId(),
                request.end().nodeId(),
                request.start().lat(),
                request.start().lon(),
                request.end().lat(),
                request.end().lon(),
                request.algorithm(),
                request.objective(),
                request.weights() == null ? null : request.weights().distanceWeight(),
                request.weights() == null ? null : request.weights().timeWeight(),
                request.roadPreferences() != null && Boolean.TRUE.equals(request.roadPreferences().avoidHighway()),
                request.roadPreferences() != null && Boolean.TRUE.equals(request.roadPreferences().preferMainRoad())
        ));

        List<RouteResponseDto.RoutePathNodeDto> path = routingService.toNodeReferences(route.pathResult().getPathNodeIds())
                .stream()
                .map(node -> new RouteResponseDto.RoutePathNodeDto(node.nodeId(), node.lat(), node.lon()))
                .toList();

        return new RouteResponseDto(
                route.pathResult().isPathFound(),
                route.algorithm(),
                route.options().objective().apiValue(),
                new RouteResponseDto.ResolvedRouteLocationDto(
                        request.start(), route.start().nodeId(), route.start().lat(), route.start().lon(), route.start().snapDistanceM()
                ),
                new RouteResponseDto.ResolvedRouteLocationDto(
                        request.end(), route.end().nodeId(), route.end().lat(), route.end().lon(), route.end().snapDistanceM()
                ),
                path,
                route.pathResult().isPathFound() ? route.distanceMeters() : null,
                path.size()
        );
    }

    private void validateRouteRequest(RouteRequestDto request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Route request body is required");
        }
        if (request.start() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Route start is required");
        }
        if (request.end() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Route end is required");
        }
        validateLocation("start", request.start());
        validateLocation("end", request.end());
    }

    private void validateLocation(String label, RouteRequestDto.RouteLocationInputDto location) {
        boolean hasNodeId = location.nodeId() != null && !location.nodeId().isBlank();
        boolean hasCoordinates = location.lat() != null && location.lon() != null;
        if (!hasNodeId && !hasCoordinates) {
            throw new ResponseStatusException(BAD_REQUEST, "Route " + label + " requires nodeId or lat/lon");
        }
    }
}
