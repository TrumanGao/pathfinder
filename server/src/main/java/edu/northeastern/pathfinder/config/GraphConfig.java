package edu.northeastern.pathfinder.config;

import edu.northeastern.pathfinder.graph.GeoJsonLoader;
import edu.northeastern.pathfinder.graph.Graph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Exposes the shared in-memory routing graph loaded by GeoJsonLoader. */
@Configuration
public class GraphConfig {

    @Bean
    public Graph routingGraph(GeoJsonLoader loader) {
        return loader.getGraph();
    }
}
