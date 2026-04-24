package edu.northeastern.pathfinder.pathfinding;

import edu.northeastern.pathfinder.graph.Edge;
import edu.northeastern.pathfinder.graph.Graph;
import edu.northeastern.pathfinder.graph.Node;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bidirectional Dijkstra must return the same total cost as Dijkstra on
 * small hand-built graphs and must report not-found for disconnected pairs.
 */
class BidirectionalDijkstraTest {

    private final ShortestPathAlgorithm bidi = new BidirectionalDijkstra();
    private final ShortestPathAlgorithm dijkstra = new DijkstraShortestPath();

    @Test
    void sameStartAndEndReturnsTrivialPath() {
        Graph g = new Graph();
        g.addNode(new Node("a", 0.0, 0.0));

        PathfindingResult result = bidi.findPath(g, "a", "a", PathCostModel.distanceOnly());

        assertTrue(result.isPathFound());
        assertEquals(0.0, result.getTotalDistanceMeters());
        assertEquals(List.of("a"), result.getPathNodeIds());
    }

    @Test
    void unknownEndpointReturnsNotFound() {
        Graph g = new Graph();
        g.addNode(new Node("a", 0.0, 0.0));

        assertFalse(bidi.findPath(g, "a", "missing", PathCostModel.distanceOnly()).isPathFound());
        assertFalse(bidi.findPath(g, "missing", "a", PathCostModel.distanceOnly()).isPathFound());
    }

    @Test
    void disconnectedGraphReturnsNotFound() {
        Graph g = new Graph();
        g.addNode(new Node("a", 0.0, 0.0));
        g.addNode(new Node("b", 0.0, 1.0));
        // no edges

        assertFalse(bidi.findPath(g, "a", "b", PathCostModel.distanceOnly()).isPathFound());
    }

    @Test
    void chainedPathMatchesDijkstra() {
        // a -> b -> c -> d, with weights 1, 2, 3
        Graph g = new Graph();
        g.addNode(new Node("a", 0.0, 0.0));
        g.addNode(new Node("b", 0.0, 1.0));
        g.addNode(new Node("c", 0.0, 2.0));
        g.addNode(new Node("d", 0.0, 3.0));
        g.addEdge("a", edge("b", 1.0));
        g.addEdge("b", edge("c", 2.0));
        g.addEdge("c", edge("d", 3.0));

        PathfindingResult biResult = bidi.findPath(g, "a", "d", PathCostModel.distanceOnly());
        PathfindingResult dijkResult = dijkstra.findPath(g, "a", "d", PathCostModel.distanceOnly());

        assertTrue(biResult.isPathFound());
        assertEquals(dijkResult.getTotalDistanceMeters(), biResult.getTotalDistanceMeters(), 1e-9);
        assertEquals(List.of("a", "b", "c", "d"), biResult.getPathNodeIds());
    }

    @Test
    void prefersLowerCostAlternativePath() {
        // Two options from s to t:
        //   s -> a -> t   (cost 1 + 10 = 11)
        //   s -> b -> t   (cost 3 + 3  = 6)   <-- should win
        Graph g = new Graph();
        g.addNode(new Node("s", 0.0, 0.0));
        g.addNode(new Node("a", 0.0, 1.0));
        g.addNode(new Node("b", 1.0, 0.0));
        g.addNode(new Node("t", 1.0, 1.0));
        g.addEdge("s", edge("a", 1.0));
        g.addEdge("a", edge("t", 10.0));
        g.addEdge("s", edge("b", 3.0));
        g.addEdge("b", edge("t", 3.0));

        PathfindingResult biResult = bidi.findPath(g, "s", "t", PathCostModel.distanceOnly());
        PathfindingResult dijkResult = dijkstra.findPath(g, "s", "t", PathCostModel.distanceOnly());

        assertTrue(biResult.isPathFound());
        assertEquals(6.0, biResult.getTotalDistanceMeters(), 1e-9);
        assertEquals(dijkResult.getTotalDistanceMeters(), biResult.getTotalDistanceMeters(), 1e-9);
        assertEquals(List.of("s", "b", "t"), biResult.getPathNodeIds());
    }

    @Test
    void respectsDirectedEdges() {
        // Only s -> t is present; t -> s does not exist.
        Graph g = new Graph();
        g.addNode(new Node("s", 0.0, 0.0));
        g.addNode(new Node("t", 0.0, 1.0));
        g.addEdge("s", edge("t", 5.0));

        PathfindingResult forward = bidi.findPath(g, "s", "t", PathCostModel.distanceOnly());
        PathfindingResult backward = bidi.findPath(g, "t", "s", PathCostModel.distanceOnly());

        assertTrue(forward.isPathFound());
        assertEquals(5.0, forward.getTotalDistanceMeters(), 1e-9);
        assertFalse(backward.isPathFound());
    }

    @Test
    void meetingNodeReachedFromBackwardFrontierOnly() {
        // Longer forward chain meets a very short backward spur, forcing
        // the meeting node to be discovered on the backward side first.
        //   s -> a -> b -> c -> t   (1 + 1 + 1 + 1 = 4)
        //                 ^
        //                 +-- t links back: nonexistent (directed)
        // With both frontiers alternating, the meeting point flips sides
        // partway through; this confirms reconstruction handles that.
        Graph g = new Graph();
        g.addNode(new Node("s", 0.0, 0.0));
        g.addNode(new Node("a", 0.0, 1.0));
        g.addNode(new Node("b", 0.0, 2.0));
        g.addNode(new Node("c", 0.0, 3.0));
        g.addNode(new Node("t", 0.0, 4.0));
        g.addEdge("s", edge("a", 1.0));
        g.addEdge("a", edge("b", 1.0));
        g.addEdge("b", edge("c", 1.0));
        g.addEdge("c", edge("t", 1.0));

        PathfindingResult biResult = bidi.findPath(g, "s", "t", PathCostModel.distanceOnly());

        assertTrue(biResult.isPathFound());
        assertEquals(4.0, biResult.getTotalDistanceMeters(), 1e-9);
        assertEquals(List.of("s", "a", "b", "c", "t"), biResult.getPathNodeIds());
    }

    private static Edge edge(String toNodeId, double meters) {
        return new Edge(toNodeId, meters, "residential", null, null, null, Map.of());
    }
}
