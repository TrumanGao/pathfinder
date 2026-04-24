package edu.northeastern.pathfinder.service;

import edu.northeastern.pathfinder.config.RoutingProperties;
import edu.northeastern.pathfinder.graph.Edge;
import edu.northeastern.pathfinder.graph.Graph;
import edu.northeastern.pathfinder.graph.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingServicePolicyTest {
    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        Graph graph = new Graph();
        graph.addNode(new Node("A", 0.00000, 0.0000));
        graph.addNode(new Node("B", 0.00675, 0.0000));
        graph.addNode(new Node("C", 0.00675, 0.0045));
        graph.addNode(new Node("D", 0.01350, 0.0000));

        graph.addEdge("A", edgeTo("B", 1000.0, "primary", null));
        graph.addEdge("B", edgeTo("D", 1000.0, "primary", null));
        graph.addEdge("A", edgeTo("C", 950.0, "residential", null));
        graph.addEdge("C", edgeTo("D", 950.0, "residential", null));

        RoutingProperties routingProperties = new RoutingProperties();
        routingProperties.setDefaultAlgorithm("astar");
        routingProperties.setDefaultObjective("distance");
        routingProperties.setReferenceSpeedKph(50.0);
        routingProperties.setFallbackSpeedKph(30.0);
        routingProperties.setSpeedKphByHighway(Map.of(
                "primary", 60.0,
                "residential", 20.0
        ));
        routingProperties.setAvoidHighwayMultipliers(Map.of(
                "primary", 3.0
        ));
        routingProperties.setPreferMainRoadMultipliers(Map.of(
                "primary", 0.75
        ));
        RoutingProperties.BalancedWeights balanced = new RoutingProperties.BalancedWeights();
        balanced.setDistanceWeight(0.5);
        balanced.setTimeWeight(0.5);
        routingProperties.setBalanced(balanced);

        NearestNodeService nearestNodeService = new NearestNodeService(graph);
        SpeedResolver speedResolver = new SpeedResolver(routingProperties);
        RoutingCostPolicy routingCostPolicy = new RoutingCostPolicy(routingProperties, speedResolver);
        routingService = new RoutingService(graph, nearestNodeService, routingProperties, routingCostPolicy, speedResolver);
    }

    @Test
    void distanceObjectiveStillWorks() {
        RoutingService.RouteComputation result = routingService.route(new RoutingService.RouteQuery(
                "A", "D", null, null, null, null,
                "dijkstra", "distance", null, null, false, false
        ));

        assertTrue(result.pathResult().isPathFound());
        assertEquals(List.of("A", "C", "D"), result.pathResult().getPathNodeIds());
        assertEquals(1900.0, result.distanceMeters(), 0.0001);
        assertEquals("distance", result.options().objective().apiValue());
    }

    @Test
    void timeObjectiveWorksWithAStar() {
        RoutingService.RouteComputation result = routingService.route(new RoutingService.RouteQuery(
                "A", "D", null, null, null, null,
                "astar", "time", null, null, false, false
        ));

        assertTrue(result.pathResult().isPathFound());
        assertEquals(List.of("A", "B", "D"), result.pathResult().getPathNodeIds());
        assertEquals("time", result.options().objective().apiValue());
    }

    @Test
    void balancedObjectiveWorks() {
        RoutingService.RouteComputation result = routingService.route(new RoutingService.RouteQuery(
                "A", "D", null, null, null, null,
                "dijkstra", "balanced", null, null, false, false
        ));

        assertTrue(result.pathResult().isPathFound());
        assertEquals(List.of("A", "B", "D"), result.pathResult().getPathNodeIds());
        assertEquals("balanced", result.options().objective().apiValue());
    }

    @Test
    void avoidHighwayCanChangeSelectedPath() {
        RoutingService.RouteComputation result = routingService.route(new RoutingService.RouteQuery(
                "A", "D", null, null, null, null,
                "dijkstra", "time", null, null, true, false
        ));

        assertTrue(result.pathResult().isPathFound());
        assertEquals(List.of("A", "C", "D"), result.pathResult().getPathNodeIds());
    }

    @Test
    void preferMainRoadCanChangeSelectedPath() {
        RoutingService.RouteComputation result = routingService.route(new RoutingService.RouteQuery(
                "A", "D", null, null, null, null,
                "dijkstra", "distance", null, null, false, true
        ));

        assertTrue(result.pathResult().isPathFound());
        assertEquals(List.of("A", "B", "D"), result.pathResult().getPathNodeIds());
    }

    private Edge edgeTo(String toNodeId, double distanceMeters, String highway, String maxspeedRaw) {
        return new Edge(
                toNodeId,
                distanceMeters,
                highway,
                maxspeedRaw,
                null,
                null,
                Map.of()
        );
    }
}
