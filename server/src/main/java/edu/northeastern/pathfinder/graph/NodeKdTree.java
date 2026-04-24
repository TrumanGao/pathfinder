package edu.northeastern.pathfinder.graph;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Balanced 2D kd-tree over graph nodes for fast nearest-neighbor queries.
 *
 * Points are stored as (lat, lon * cos(meanLat)). Scaling the longitude by
 * the cosine of the mean latitude of the dataset projects each point onto
 * a local tangent plane — within this region the Euclidean ordering is
 * monotonic with great-circle distance, so the tree's pruning tests are
 * correct even though the query metric is haversine.
 *
 * Build: O(N log N) expected via in-place quickselect on a shared index
 * array. No recursion-level string/list allocation — one {@code int[]}
 * reused across all splits.
 *
 * Query: O(log N) expected, O(N) worst case on pathological layouts.
 *
 * Memory: three int[] of size N (left, right, point index) and three
 * parallel arrays of point coordinates/ids — roughly 40 bytes per node
 * vs. ~120 bytes for a HashMap-based linear scan.
 */
public final class NodeKdTree {

    private final double[] lats;
    private final double[] lonsScaled;
    private final String[] ids;
    private final int[] left;
    private final int[] right;
    private final int root;
    private final double lonScale;

    public NodeKdTree(Collection<Node> nodes) {
        int n = nodes.size();
        this.lats = new double[n];
        this.lonsScaled = new double[n];
        this.ids = new String[n];

        double sumLat = 0.0;
        int i = 0;
        for (Node node : nodes) {
            lats[i] = node.getLat();
            lonsScaled[i] = node.getLon(); // rescaled below
            ids[i] = node.getNodeId();
            sumLat += node.getLat();
            i++;
        }

        double meanLat = n == 0 ? 0.0 : sumLat / n;
        this.lonScale = Math.cos(Math.toRadians(meanLat));
        for (int j = 0; j < n; j++) {
            lonsScaled[j] = lonsScaled[j] * lonScale;
        }

        this.left = new int[n];
        this.right = new int[n];
        Arrays.fill(this.left, -1);
        Arrays.fill(this.right, -1);

        int[] indices = new int[n];
        for (int j = 0; j < n; j++) indices[j] = j;

        this.root = n == 0 ? -1 : build(indices, 0, n, 0);
    }

    /**
     * Recursively build a balanced kd-tree over indices[lo, hi).
     * Partitions in place around the median on the current axis.
     */
    private int build(int[] idx, int lo, int hi, int depth) {
        if (lo >= hi) return -1;
        int axis = depth & 1;
        int mid = (lo + hi) >>> 1;
        selectK(idx, lo, hi - 1, mid, axis);

        int here = idx[mid];
        left[here] = build(idx, lo, mid, depth + 1);
        right[here] = build(idx, mid + 1, hi, depth + 1);
        return here;
    }

    /**
     * Quickselect: rearrange {@code idx[lo..hi]} (inclusive) so that
     * {@code idx[k]} is the k-th smallest on the chosen axis.
     * Randomised pivot to avoid O(N^2) on sorted input.
     */
    private void selectK(int[] idx, int lo, int hi, int k, int axis) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        while (lo < hi) {
            int pivotIdx = lo + rng.nextInt(hi - lo + 1);
            int newPivot = partition(idx, lo, hi, pivotIdx, axis);
            if (newPivot == k) return;
            if (k < newPivot) hi = newPivot - 1;
            else lo = newPivot + 1;
        }
    }

    private int partition(int[] idx, int lo, int hi, int pivotIdx, int axis) {
        double pivotValue = axisValue(idx[pivotIdx], axis);
        swap(idx, pivotIdx, hi);
        int store = lo;
        for (int i = lo; i < hi; i++) {
            if (axisValue(idx[i], axis) < pivotValue) {
                swap(idx, store, i);
                store++;
            }
        }
        swap(idx, store, hi);
        return store;
    }

    private double axisValue(int pointIdx, int axis) {
        return axis == 0 ? lats[pointIdx] : lonsScaled[pointIdx];
    }

    private static void swap(int[] a, int i, int j) {
        if (i == j) return;
        int tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
    }

    /**
     * Find the node nearest to (queryLat, queryLon) by great-circle distance.
     * Returns null if the tree is empty.
     */
    public NearestMatch nearest(double queryLat, double queryLon) {
        if (root < 0) return null;
        double qLat = queryLat;
        double qLonScaled = queryLon * lonScale;

        // Mutable holder for best-so-far; avoid boxing by passing arrays.
        double[] bestSq = { Double.POSITIVE_INFINITY };
        int[] bestIdx = { -1 };

        searchNearest(root, qLat, qLonScaled, 0, bestSq, bestIdx);

        int found = bestIdx[0];
        double haversine = haversineMeters(
                queryLat, queryLon,
                lats[found], lonsScaled[found] / lonScale
        );
        return new NearestMatch(ids[found], haversine);
    }

    private void searchNearest(int node, double qLat, double qLon, int depth, double[] bestSq, int[] bestIdx) {
        if (node < 0) return;

        double dLat = qLat - lats[node];
        double dLon = qLon - lonsScaled[node];
        double distSq = dLat * dLat + dLon * dLon;
        if (distSq < bestSq[0]) {
            bestSq[0] = distSq;
            bestIdx[0] = node;
        }

        int axis = depth & 1;
        double diff = axis == 0 ? dLat : dLon;

        int near = diff < 0 ? left[node] : right[node];
        int far = diff < 0 ? right[node] : left[node];

        searchNearest(near, qLat, qLon, depth + 1, bestSq, bestIdx);

        // Only descend into the far side if its axis-aligned separation
        // could still beat the current best — the standard kd-tree prune.
        if (diff * diff < bestSq[0]) {
            searchNearest(far, qLat, qLon, depth + 1, bestSq, bestIdx);
        }
    }

    public int size() {
        return ids.length;
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        return r * (2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a)));
    }

    public record NearestMatch(String nodeId, double distanceMeters) {
    }
}
