package edu.northeastern.pathfinder.service;

import edu.northeastern.pathfinder.graph.Graph;
import edu.northeastern.pathfinder.graph.NodeKdTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves coordinates to the nearest graph node using a 2D kd-tree.
 *
 * The tree is built once at startup; queries run in O(log N) expected
 * instead of the O(N) linear scan used previously. With ~1.88M nodes this
 * is the difference between ~10 ms and a few microseconds per map click.
 */
@Service
public class NearestNodeService {
    private static final Logger log = LoggerFactory.getLogger(NearestNodeService.class);

    private final NodeKdTree index;

    public NearestNodeService(Graph graph) {
        long t0 = System.nanoTime();
        this.index = new NodeKdTree(graph.getNodesById().values());
        long durationMs = (System.nanoTime() - t0) / 1_000_000L;
        log.info("NodeKdTree built in {} ms over {} nodes", durationMs, index.size());
    }

    public NearestNodeMatch findNearestNode(double lat, double lon) {
        NodeKdTree.NearestMatch match = index.nearest(lat, lon);
        if (match == null) {
            throw new IllegalStateException("Graph has no nodes");
        }
        return new NearestNodeMatch(match.nodeId(), match.distanceMeters());
    }

    public record NearestNodeMatch(String nodeId, double distanceMeters) {
    }
}
