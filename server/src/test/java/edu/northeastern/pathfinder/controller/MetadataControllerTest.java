package edu.northeastern.pathfinder.controller;

import edu.northeastern.pathfinder.dto.MetadataResponseDto;
import edu.northeastern.pathfinder.service.RoutingOptions;
import edu.northeastern.pathfinder.service.RoutingService;
import edu.northeastern.pathfinder.service.SearchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetadataControllerTest {

    @Test
    void metadataExposesSupportedObjectives() {
        RoutingService routingService = mock(RoutingService.class);
        SearchService searchService = mock(SearchService.class);

        when(routingService.getSupportedAlgorithms()).thenReturn(List.of("astar", "dijkstra"));
        when(routingService.getDefaultAlgorithm()).thenReturn("astar");
        when(routingService.getSupportedObjectives()).thenReturn(List.of("distance", "time", "balanced"));
        when(routingService.getDefaultObjective()).thenReturn("distance");
        when(routingService.getDefaultBalancedWeights()).thenReturn(new RoutingOptions.BalancedWeights(0.5, 0.5));
        when(routingService.getSupportedRoadPreferences()).thenReturn(List.of("avoidHighway", "preferMainRoad"));
        when(routingService.getNodeCount()).thenReturn(12);
        when(routingService.getEdgeCount()).thenReturn(24);

        when(searchService.getSupportedTypes()).thenReturn(List.of("food", "road"));
        when(searchService.getDefaultLimit()).thenReturn(10);
        when(searchService.getMaxLimit()).thenReturn(50);

        MetadataController controller = new MetadataController(routingService, searchService);
        MetadataResponseDto metadata = controller.metadata();

        assertIterableEquals(List.of("distance", "time", "balanced"), metadata.routing().supportedObjectives());
        assertEquals("distance", metadata.routing().defaultObjective());
        assertEquals(0.5, metadata.routing().defaultWeights().distanceWeight());
        assertEquals(0.5, metadata.routing().defaultWeights().timeWeight());
        assertIterableEquals(List.of("avoidHighway", "preferMainRoad"), metadata.routing().supportedRoadPreferences());
        assertEquals("astar", metadata.routing().defaultAlgorithm());
    }
}
