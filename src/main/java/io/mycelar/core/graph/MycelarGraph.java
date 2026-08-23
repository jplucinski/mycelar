package io.mycelar.core.graph;

import io.mycelar.core.validation.GraphValidationException;
import io.mycelar.core.validation.GraphValidator;
import io.mycelar.core.validation.Violation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public final class MycelarGraph {

    private static final GraphValidator VALIDATOR = new GraphValidator();

    private final Map<NodeId, GraphNode> nodes;
    private final Map<EdgeId, GraphEdge> edges;
    private final Map<NodeId, List<GraphEdge>> outgoing;
    private final List<NodeId> walkRoots;

    private MycelarGraph(List<GraphNode> nodeList, List<GraphEdge> edgeList) {
        VALIDATOR.validate(nodeList, edgeList);

        Map<NodeId, GraphNode> nodeMap = new LinkedHashMap<>();
        for (GraphNode node : nodeList) {
            nodeMap.put(node.id(), node);
        }
        Map<EdgeId, GraphEdge> edgeMap = new LinkedHashMap<>();
        Map<NodeId, List<GraphEdge>> out = new LinkedHashMap<>();
        for (NodeId id : nodeMap.keySet()) {
            out.put(id, new ArrayList<>());
        }
        for (GraphEdge edge : edgeList) {
            edgeMap.put(edge.id(), edge);
            out.computeIfAbsent(edge.source(), k -> new ArrayList<>()).add(edge);
        }
        Map<NodeId, List<GraphEdge>> frozenOut = new LinkedHashMap<>();
        for (Map.Entry<NodeId, List<GraphEdge>> e : out.entrySet()) {
            frozenOut.put(e.getKey(), List.copyOf(e.getValue()));
        }

        this.nodes = Collections.unmodifiableMap(nodeMap);
        this.edges = Collections.unmodifiableMap(edgeMap);
        this.outgoing = Collections.unmodifiableMap(frozenOut);
        this.walkRoots = List.copyOf(computeWalkRoots(nodeMap.keySet(), edgeList));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<GraphNode> node(NodeId id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public Collection<GraphNode> nodes() {
        return nodes.values();
    }

    public Collection<GraphEdge> edges() {
        return edges.values();
    }

    public List<GraphEdge> outgoing(NodeId id) {
        List<GraphEdge> list = outgoing.get(id);
        return list == null ? List.of() : list;
    }

    public List<GraphEdge> outgoing(NodeId id, EdgeType type) {
        Objects.requireNonNull(type, "type");
        return outgoing(id).stream().filter(e -> e.type() == type).toList();
    }

    public List<NodeId> walkRoots() {
        return walkRoots;
    }

    public MycelarGraph withWeights(Map<EdgeId, Double> weights) {
        Objects.requireNonNull(weights, "weights");
        List<Violation> violations = new ArrayList<>();
        for (EdgeId id : weights.keySet()) {
            if (!edges.containsKey(id)) {
                violations.add(new Violation("weights[" + id.value() + "]", "unknown EdgeId"));
            }
        }
        if (!violations.isEmpty()) {
            throw new GraphValidationException(violations);
        }
        List<GraphEdge> newEdges = new ArrayList<>(edges.size());
        for (GraphEdge edge : edges.values()) {
            Double replacement = weights.get(edge.id());
            if (replacement != null) {
                newEdges.add(
                        new GraphEdge(
                                edge.id(),
                                edge.source(),
                                edge.target(),
                                edge.type(),
                                replacement,
                                edge.metadata()));
            } else {
                newEdges.add(edge);
            }
        }
        return new MycelarGraph(List.copyOf(nodes.values()), newEdges);
    }

    private static List<NodeId> computeWalkRoots(Set<NodeId> allNodes, List<GraphEdge> edgeList) {
        Map<NodeId, Integer> walkableInDegree = new HashMap<>();
        Map<NodeId, List<NodeId>> walkableOut = new HashMap<>();
        for (NodeId id : allNodes) {
            walkableInDegree.put(id, 0);
            walkableOut.put(id, new ArrayList<>());
        }
        for (GraphEdge edge : edgeList) {
            if (!isWalkable(edge.type())) {
                continue;
            }
            walkableInDegree.merge(edge.target(), 1, Integer::sum);
            walkableOut.computeIfAbsent(edge.source(), k -> new ArrayList<>()).add(edge.target());
        }

        List<NodeId> roots = new ArrayList<>();
        for (NodeId id : allNodes) {
            if (walkableInDegree.getOrDefault(id, 0) == 0) {
                roots.add(id);
            }
        }

        Set<NodeId> reachable = new HashSet<>();
        Queue<NodeId> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            NodeId current = queue.poll();
            if (!reachable.add(current)) {
                continue;
            }
            for (NodeId next : walkableOut.getOrDefault(current, List.of())) {
                queue.add(next);
            }
        }

        for (NodeId id : allNodes) {
            if (!reachable.contains(id)) {
                roots.add(id);
            }
        }

        roots.sort(Comparator.comparing(NodeId::value));
        return roots;
    }

    private static boolean isWalkable(EdgeType type) {
        return type == EdgeType.CONTAINS || type == EdgeType.RELATES_TO;
    }

    public static final class Builder {
        private final List<GraphNode> nodes = new ArrayList<>();
        private final List<GraphEdge> edges = new ArrayList<>();

        private Builder() {}

        public Builder node(GraphNode node) {
            nodes.add(Objects.requireNonNull(node, "node"));
            return this;
        }

        public Builder edge(GraphEdge edge) {
            edges.add(Objects.requireNonNull(edge, "edge"));
            return this;
        }

        public MycelarGraph build() {
            return new MycelarGraph(List.copyOf(nodes), List.copyOf(edges));
        }
    }
}
