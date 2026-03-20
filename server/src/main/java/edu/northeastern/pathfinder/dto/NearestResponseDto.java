package edu.northeastern.pathfinder.dto;

/**
 * EN: Response DTO for nearest-node lookup.
 * It reports the caller input and the snapped routable graph node,
 * but it does not try to classify the node or infer route quality.
 * 中文：最近节点查询接口的响应 DTO。
 * 它返回调用方输入坐标以及匹配到的可路由图节点，
 * 但不会进一步推断节点类别或路线质量。
 */
public record NearestResponseDto(
        boolean matched,
        InputCoordinateDto input,
        String nodeId,
        double lat,
        double lon,
        double distanceM
) {
    /**
     * EN: Raw coordinate input supplied by the caller.
     * 中文：调用方提供的原始坐标输入。
     */
    public record InputCoordinateDto(double lat, double lon) {
    }
}
