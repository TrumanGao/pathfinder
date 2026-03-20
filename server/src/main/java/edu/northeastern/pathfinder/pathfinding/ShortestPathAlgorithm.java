package edu.northeastern.pathfinder.pathfinding;

import edu.northeastern.pathfinder.graph.Graph;

/**
 * EN: Minimal common contract for shortest-path algorithms used by the routing layer.
 * Implementations keep their existing search structure, but can now receive a request-scoped
 * cost model so different route requests do not need globally mutable graph weights.
 * 中文：路由层使用的最小最短路径算法契约。
 * 具体算法仍保持原有搜索结构，但现在可以接收“单次请求范围内”的成本模型，
 * 这样不同请求无需修改全局图权重。
 */
public interface ShortestPathAlgorithm {
    default PathfindingResult findPath(Graph graph, String startNodeId, String endNodeId) {
        return findPath(graph, startNodeId, endNodeId, PathCostModel.distanceOnly());
    }

    PathfindingResult findPath(Graph graph, String startNodeId, String endNodeId, PathCostModel costModel);
}
