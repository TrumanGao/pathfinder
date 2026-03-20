package edu.northeastern.pathfinder.dto;

import java.util.List;

/**
 * EN: Response DTO for the route endpoint.
 * It reports the algorithm actually used, the resolved objective, snapped endpoints, and the
 * returned path. The response stays honest about current capability and does not expose features
 * that are not implemented yet.
 * 中文：路由端点的响应 DTO。
 * 它会返回实际使用的算法、解析后的目标、吸附后的端点，以及返回路径。
 * 响应会如实反映当前能力，不暴露尚未实现的特性。
 */
public record RouteResponseDto(
        boolean success,
        String algorithm,
        String objective,
        ResolvedRouteLocationDto start,
        ResolvedRouteLocationDto end,
        List<RoutePathNodeDto> path,
        Double distanceM,
        int pathNodeCount
) {
    /**
     * EN: Resolved route endpoint after node-id lookup or coordinate snapping.
     * 中文：在节点解析或坐标吸附之后得到的最终路由端点。
     */
    public record ResolvedRouteLocationDto(
            RouteRequestDto.RouteLocationInputDto input,
            String resolvedNodeId,
            double lat,
            double lon,
            double snapDistanceM
    ) {
    }

    /**
     * EN: One node in the returned route path.
     * 中文：返回路径中的单个节点。
     */
    public record RoutePathNodeDto(
            String nodeId,
            double lat,
            double lon
    ) {
    }
}
