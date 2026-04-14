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
 * EN: Route API controller for the current routing capability.
 * It exposes node-id or coordinate-based routing on top of the existing graph, with the real
 * supported objectives only: distance, time, and balanced.
 * 中文：面向当前路由能力的接口控制器。
 * 它在现有图结构之上暴露基于 nodeId 或坐标的路由，并且只支持真实已实现的目标：
 * distance、time、balanced。
 */
@RestController
@RequestMapping("/api")
public class RouteController {
    private final RoutingService routingService;

    public RouteController(RoutingService routingService) {
        this.routingService = routingService;
    }

    /**
     * EN: Computes a route between start and end using either nodeId inputs or coordinate inputs.
     * If coordinates are provided, the backend snaps them to nearest routable graph nodes first.
     * Current limitations: only A* / Dijkstra and the current edge-cost policy are implemented;
     * this endpoint does not expose unsupported future routing systems.
     * 中文：使用起点和终点计算路线，输入既可以是 nodeId，也可以是坐标。
     * 如果提供的是坐标，后端会先将其吸附到最近的可路由图节点。
     * 当前限制：只实现了 A* / Dijkstra 与当前边成本策略；该接口不会暴露尚未实现的未来路由系统。
     */
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
                route.pathResult().isPathFound() ? route.estimatedTravelTimeSeconds() : null,
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
