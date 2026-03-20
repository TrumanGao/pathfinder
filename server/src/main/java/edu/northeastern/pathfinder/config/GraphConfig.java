package edu.northeastern.pathfinder.config;

import edu.northeastern.pathfinder.graph.GeoJsonGraphBuilder;
import edu.northeastern.pathfinder.graph.Graph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * EN: Loads and exposes the shared in-memory routing graph from GeoJSON once at startup.
 * This configuration is only responsible for graph-related initialization and should not become a generic config container.
 * 中文：在应用启动时从 GeoJSON 加载并暴露共享的内存路网图，仅加载一次。
 * 该配置类只负责图相关的初始化，不应演变成杂项配置容器。
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
