package io.mycelar.core.validation;

import io.mycelar.core.capability.CapabilityId;
import io.mycelar.core.constraint.ConstraintId;
import io.mycelar.core.context.ContextCapsule;
import io.mycelar.core.graph.CapsuleId;
import io.mycelar.core.graph.EdgeId;
import io.mycelar.core.graph.EdgeType;
import io.mycelar.core.graph.GraphEdge;
import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.graph.NodeType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GraphValidator {

    public void validate(List<GraphNode> nodes, List<GraphEdge> edges) {
        List<Violation> violations = new ArrayList<>();

        Map<NodeId, GraphNode> byId = new HashMap<>();
        Set<CapsuleId> capsuleIds = new HashSet<>();
        Set<CapabilityId> capabilityIds = new HashSet<>();
        Set<ConstraintId> constraintIds = new HashSet<>();

        for (GraphNode node : nodes) {
            String path = "nodes[" + node.id().value() + "]";
            if (byId.put(node.id(), node) != null) {
                violations.add(new Violation(path, "duplicate NodeId"));
            }
            for (ContextCapsule capsule : node.capsules()) {
                if (!capsuleIds.add(capsule.id())) {
                    violations.add(
                            new Violation(path, "duplicate CapsuleId: " + capsule.id().value()));
                }
            }
            if (node.capability().isPresent()) {
                CapabilityId capId = node.capability().get().id();
                if (!capabilityIds.add(capId)) {
                    violations.add(new Violation(path, "duplicate CapabilityId: " + capId.value()));
                }
            }
            if (node.constraint().isPresent()) {
                ConstraintId cId = node.constraint().get().id();
                if (!constraintIds.add(cId)) {
                    violations.add(new Violation(path, "duplicate ConstraintId: " + cId.value()));
                }
            }
            validatePayloadPairing(node, path, violations);
        }

        Set<EdgeId> edgeIds = new HashSet<>();
        Set<String> triples = new HashSet<>();
        Map<NodeId, Integer> outgoingCount = new HashMap<>();

        for (GraphEdge edge : edges) {
            String path = "edges[" + edge.id().value() + "]";
            if (!edgeIds.add(edge.id())) {
                violations.add(new Violation(path, "duplicate EdgeId"));
            }
            if (!byId.containsKey(edge.source())) {
                violations.add(new Violation(path, "missing source node: " + edge.source().value()));
            }
            if (!byId.containsKey(edge.target())) {
                violations.add(new Violation(path, "missing target node: " + edge.target().value()));
            }
            double w = edge.weight();
            if (Double.isNaN(w) || Double.isInfinite(w) || w < 0.0 || w > 1.0) {
                violations.add(new Violation(path, "weight must be in [0,1]"));
            }
            if (edge.source().equals(edge.target())) {
                violations.add(new Violation(path, "self-loop not allowed"));
            }
            String triple =
                    edge.source().value() + "\0" + edge.target().value() + "\0" + edge.type().name();
            if (!triples.add(triple)) {
                violations.add(new Violation(path, "duplicate edge for (source, target, type)"));
            }
            outgoingCount.merge(edge.source(), 1, Integer::sum);
            validateEdgeTyping(edge, byId, path, violations);
        }

        for (GraphNode node : nodes) {
            if (node.type() == NodeType.CONSTRAINT
                    && outgoingCount.getOrDefault(node.id(), 0) > 0) {
                violations.add(
                        new Violation(
                                "nodes[" + node.id().value() + "]",
                                "CONSTRAINT node must not have outgoing edges"));
            }
        }

        if (!violations.isEmpty()) {
            throw new GraphValidationException(violations);
        }
    }

    private static void validatePayloadPairing(
            GraphNode node, String path, List<Violation> violations) {
        boolean hasCapability = node.capability().isPresent();
        boolean hasConstraint = node.constraint().isPresent();
        switch (node.type()) {
            case CAPABILITY -> {
                if (!hasCapability) {
                    violations.add(new Violation(path, "CAPABILITY node requires capability"));
                }
                if (hasConstraint) {
                    violations.add(new Violation(path, "CAPABILITY node must not have constraint"));
                }
            }
            case CONSTRAINT -> {
                if (!hasConstraint) {
                    violations.add(new Violation(path, "CONSTRAINT node requires constraint"));
                }
                if (hasCapability) {
                    violations.add(new Violation(path, "CONSTRAINT node must not have capability"));
                }
            }
            default -> {
                if (hasCapability) {
                    violations.add(
                            new Violation(path, "non-CAPABILITY node must not have capability"));
                }
                if (hasConstraint) {
                    violations.add(
                            new Violation(path, "non-CONSTRAINT node must not have constraint"));
                }
            }
        }
    }

    private static void validateEdgeTyping(
            GraphEdge edge, Map<NodeId, GraphNode> byId, String path, List<Violation> violations) {
        GraphNode source = byId.get(edge.source());
        GraphNode target = byId.get(edge.target());
        if (source == null || target == null) {
            return;
        }
        EdgeType type = edge.type();
        if (type == EdgeType.CONTAINS || type == EdgeType.RELATES_TO) {
            if (source.type() == NodeType.CONSTRAINT || target.type() == NodeType.CONSTRAINT) {
                violations.add(
                        new Violation(
                                path, type + " must not involve CONSTRAINT endpoints"));
            }
        } else if (type == EdgeType.REQUIRES) {
            if (source.type() != NodeType.CAPABILITY) {
                violations.add(new Violation(path, "REQUIRES source must be CAPABILITY"));
            }
            if (target.type() != NodeType.CONSTRAINT) {
                violations.add(new Violation(path, "REQUIRES target must be CONSTRAINT"));
            }
        } else if (type == EdgeType.EXPANDS_TO) {
            if (source.type() == NodeType.CONSTRAINT) {
                violations.add(new Violation(path, "EXPANDS_TO source must not be CONSTRAINT"));
            }
        }
    }
}
