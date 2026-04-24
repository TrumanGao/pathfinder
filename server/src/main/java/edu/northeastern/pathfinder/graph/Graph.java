package edu.northeastern.pathfinder.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory routing topology: node table + outgoing adjacency list.
 *
 * The reverse adjacency index is built lazily on first request and cached;
 * bidirectional search is currently the only caller, so paying the build
 * cost upfront would be wasted work for unidirectional queries.
 */
public final class Graph {
    private final Map<String, Node> nodesById = new HashMap<>();
    private final Map<String, List<Edge>> outgoingAdj = new HashMap<>();

    private volatile Map<String, List<ReverseEdge>> incomingAdj;

    public void addNode(Node node) {
        nodesById.putIfAbsent(node.getNodeId(), node);
    }

    public void addEdge(String fromNodeId, Edge edge) {
        outgoingAdj.computeIfAbsent(fromNodeId, ignored -> new ArrayList<>()).add(edge);
        incomingAdj = null; // invalidate reverse index on any mutation
    }

    public Optional<Node> getNode(String nodeId) {
        return Optional.ofNullable(nodesById.get(nodeId));
    }

    public List<Edge> getOutgoing(String nodeId) {
        List<Edge> edges = outgoingAdj.get(nodeId);
        return edges == null ? List.of() : Collections.unmodifiableList(edges);
    }

    /**
     * Edges ending at the given node, for backward search from a goal.
     * Each {@link ReverseEdge} carries the original source node id and
     * the same cost attributes as the forward edge.
     */
    public List<ReverseEdge> getIncoming(String nodeId) {
        Map<String, List<ReverseEdge>> index = incomingAdj;
        if (index == null) {
            index = buildIncoming();
            incomingAdj = index;
        }
        List<ReverseEdge> edges = index.get(nodeId);
        return edges == null ? List.of() : edges;
    }

    private synchronized Map<String, List<ReverseEdge>> buildIncoming() {
        if (incomingAdj != null) return incomingAdj;
        Map<String, List<ReverseEdge>> index = new HashMap<>(outgoingAdj.size() * 2);
        for (Map.Entry<String, List<Edge>> entry : outgoingAdj.entrySet()) {
            String fromId = entry.getKey();
            for (Edge edge : entry.getValue()) {
                index.computeIfAbsent(edge.getToNodeId(), k -> new ArrayList<>())
                        .add(new ReverseEdge(fromId, edge));
            }
        }
        for (Map.Entry<String, List<ReverseEdge>> entry : index.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(index);
    }

    public Map<String, Node> getNodesById() {
        return Collections.unmodifiableMap(nodesById);
    }

    public Map<String, List<Edge>> getOutgoingAdj() {
        Map<String, List<Edge>> copy = new HashMap<>();
        for (Map.Entry<String, List<Edge>> entry : outgoingAdj.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    public int getNodeCount() {
        return nodesById.size();
    }

    public int getEdgeCount() {
        int total = 0;
        for (List<Edge> edges : outgoingAdj.values()) {
            total += edges.size();
        }
        return total;
    }
}
