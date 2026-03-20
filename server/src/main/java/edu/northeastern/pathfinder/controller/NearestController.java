package edu.northeastern.pathfinder.controller;

import edu.northeastern.pathfinder.dto.NearestResponseDto;
import edu.northeastern.pathfinder.service.NearestNodeService;
import edu.northeastern.pathfinder.service.RoutingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * EN: API controller for coordinate-to-nearest-routable-node lookup.
 * It exposes only the current snapping capability and does not perform routing by itself.
 * 中文：用于“坐标 -> 最近可路由节点”查询的接口控制器。
 * 它只暴露当前的吸附能力，本身不负责执行路线计算。
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

    /**
     * EN: Snaps input coordinates to the nearest routable graph node using the current linear-scan nearest-node service.
     * Current limitations: no spatial index and no route computation in this endpoint.
     * 中文：使用当前线性扫描的最近节点服务，将输入坐标吸附到最近的可路由图节点。
     * 当前限制：该接口不使用空间索引，也不执行路线计算。
     */
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
