package edu.northeastern.pathfinder.config;

import edu.northeastern.pathfinder.graph.GeoJsonLoader;
import edu.northeastern.pathfinder.graph.Graph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * EN: Exposes the shared in-memory routing graph loaded by GeoJsonLoader.
 * 中文：暴露由 GeoJsonLoader 加载的共享内存路网图。
 */
@Configuration
public class GraphConfig {

    @Bean
    public Graph routingGraph(GeoJsonLoader loader) {
        return loader.getGraph();
    }
}
