package edu.northeastern.pathfinder.config;

import edu.northeastern.pathfinder.graph.GeoJsonGraphBuilder;
import edu.northeastern.pathfinder.graph.Graph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Loads the shared in-memory routing graph from GeoJSON at startup.
 */
@Configuration
public class GraphConfig {

    @Bean
    public Graph routingGraph(
            @Value("${pathfinder.graph.geojson-path:../data/full.geojson}") String geoJsonPath
    ) {
        try {
            return new GeoJsonGraphBuilder().build(Paths.get(geoJsonPath)).getGraph();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load graph from GeoJSON: " + geoJsonPath, e);
        }
    }
}
