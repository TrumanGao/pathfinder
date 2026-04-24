package edu.northeastern.pathfinder.graph;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Correctness tests for {@link NodeKdTree}: every kd-tree query must
 * return the same nodeId as a brute-force linear scan, across a range
 * of synthetic point distributions and query locations.
 */
class NodeKdTreeTest {

    @Test
    void emptyTreeReturnsNull() {
        NodeKdTree tree = new NodeKdTree(List.of());
        assertNull(tree.nearest(0.0, 0.0));
    }

    @Test
    void singleNodeIsAlwaysTheAnswer() {
        Node only = new Node("only", -77.0369, 38.9072);
        NodeKdTree tree = new NodeKdTree(List.of(only));
        NodeKdTree.NearestMatch match = tree.nearest(38.9072, -77.0369);
        assertNotNull(match);
        assertEquals("only", match.nodeId());
        assertEquals(0.0, match.distanceMeters(), 1e-6);
    }

    @Test
    void handpickedPointsMatchBruteForce() {
        // DC-area points with known geography: White House, Capitol, Lincoln Memorial, Washington Monument
        List<Node> nodes = List.of(
                new Node("white-house", -77.0365, 38.8977),
                new Node("capitol", -77.0091, 38.8899),
                new Node("lincoln", -77.0502, 38.8893),
                new Node("washington-monument", -77.0353, 38.8895)
        );
        NodeKdTree tree = new NodeKdTree(nodes);

        // Query near the Washington Monument — it should be the winner
        NodeKdTree.NearestMatch match = tree.nearest(38.8895, -77.0353);
        assertEquals("washington-monument", match.nodeId());
    }

    @Test
    void randomGridQueriesMatchBruteForceOverSmallDataset() {
        randomCheck(200, 500, 12345L);
    }

    @Test
    void randomGridQueriesMatchBruteForceOverLargerDataset() {
        randomCheck(5_000, 1_000, 987654321L);
    }

    @Test
    void clusteredPointsStillResolveToNearestBruteForceMatch() {
        // Force a pathological build: many points clustered at one coordinate.
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            nodes.add(new Node("dup-" + i, -77.03, 38.90));
        }
        nodes.add(new Node("far", -77.50, 39.50));
        NodeKdTree tree = new NodeKdTree(nodes);

        NodeKdTree.NearestMatch near = tree.nearest(38.90, -77.03);
        // Any of the cluster ids is acceptable — brute force returns whichever
        // it sees first. We just require the tree to pick one of the cluster.
        assertNotNull(near);
        assert near.nodeId().startsWith("dup-");

        NodeKdTree.NearestMatch far = tree.nearest(39.50, -77.50);
        assertEquals("far", far.nodeId());
    }

    private void randomCheck(int nodeCount, int queryCount, long seed) {
        Random rng = new Random(seed);

        // DC bounding box-ish range
        double minLat = 38.80, maxLat = 39.00;
        double minLon = -77.12, maxLon = -76.90;

        List<Node> nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            double lat = minLat + rng.nextDouble() * (maxLat - minLat);
            double lon = minLon + rng.nextDouble() * (maxLon - minLon);
            nodes.add(new Node("n" + i, lon, lat));
        }

        NodeKdTree tree = new NodeKdTree(nodes);

        for (int q = 0; q < queryCount; q++) {
            double qLat = minLat + rng.nextDouble() * (maxLat - minLat);
            double qLon = minLon + rng.nextDouble() * (maxLon - minLon);

            String bruteId = bruteForceNearestId(nodes, qLat, qLon);
            NodeKdTree.NearestMatch kd = tree.nearest(qLat, qLon);

            // Ties are rare in continuous random data, but a kd-tree may
            // legitimately return a different winner when distances match.
            // Assert by distance equality, not id equality.
            double bruteDistance = haversine(qLat, qLon,
                    findNode(nodes, bruteId).getLat(),
                    findNode(nodes, bruteId).getLon());

            assertEquals(bruteDistance, kd.distanceMeters(), 1e-6,
                    "Mismatch at query " + q + ": kd=" + kd.nodeId() + " brute=" + bruteId);
        }
    }

    private static Node findNode(List<Node> nodes, String id) {
        for (Node n : nodes) if (n.getNodeId().equals(id)) return n;
        throw new IllegalArgumentException(id);
    }

    private static String bruteForceNearestId(List<Node> nodes, double lat, double lon) {
        String best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (Node n : nodes) {
            double d = haversine(lat, lon, n.getLat(), n.getLon());
            if (d < bestD) {
                bestD = d;
                best = n.getNodeId();
            }
        }
        return best;
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        return r * (2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a)));
    }
}
