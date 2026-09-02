package io.mycelar.core.capability;

import io.mycelar.core.constraint.Constraint;
import io.mycelar.core.constraint.ConstraintDecision;
import io.mycelar.core.constraint.ConstraintEvaluator;
import io.mycelar.core.constraint.ConstraintId;
import io.mycelar.core.graph.EdgeType;
import io.mycelar.core.graph.GraphEdge;
import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.MycelarGraph;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.graph.NodeType;
import io.mycelar.core.routing.RoutePath;
import io.mycelar.core.routing.RoutingContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CapabilityResolver {

    public CapabilityScope resolve(
            MycelarGraph graph,
            RoutePath path,
            RoutingContext context,
            List<ConstraintEvaluator> evaluators) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(evaluators, "evaluators");

        LinkedHashSet<CapabilityId> allowed = new LinkedHashSet<>();
        LinkedHashSet<BlockedCapability> blocked = new LinkedHashSet<>();

        for (GraphNode node : collectInScope(graph, path)) {
            CapabilityId capabilityId = node.capability().orElseThrow().id();
            List<Constraint> constraints = requiredConstraints(graph, node.id());
            if (constraints.isEmpty()) {
                allowed.add(capabilityId);
                continue;
            }

            ConstraintDecision worst = ConstraintDecision.ALLOW;
            List<ConstraintId> nonAllowIds = new ArrayList<>();
            for (Constraint constraint : constraints) {
                ConstraintDecision decision = evaluate(constraint, context, evaluators);
                if (decision != ConstraintDecision.ALLOW) {
                    nonAllowIds.add(constraint.id());
                }
                if (rank(decision) > rank(worst)) {
                    worst = decision;
                }
            }

            if (worst == ConstraintDecision.ALLOW) {
                allowed.add(capabilityId);
            } else {
                blocked.add(new BlockedCapability(capabilityId, worst, nonAllowIds));
            }
        }

        return new CapabilityScope(allowed, blocked);
    }

    private static LinkedHashSet<GraphNode> collectInScope(MycelarGraph graph, RoutePath path) {
        LinkedHashSet<GraphNode> inScope = new LinkedHashSet<>();
        for (NodeId nodeId : path.nodeIds()) {
            Optional<GraphNode> maybeNode = graph.node(nodeId);
            if (maybeNode.isEmpty()) {
                continue;
            }
            GraphNode node = maybeNode.get();
            if (node.type() == NodeType.CAPABILITY) {
                inScope.add(node);
            }
            for (GraphEdge edge : graph.outgoing(nodeId, EdgeType.CONTAINS)) {
                graph.node(edge.target())
                        .filter(child -> child.type() == NodeType.CAPABILITY)
                        .ifPresent(inScope::add);
            }
        }
        return inScope;
    }

    private static List<Constraint> requiredConstraints(MycelarGraph graph, NodeId capabilityNodeId) {
        List<Constraint> constraints = new ArrayList<>();
        for (GraphEdge edge : graph.outgoing(capabilityNodeId, EdgeType.REQUIRES)) {
            graph.node(edge.target())
                    .flatMap(GraphNode::constraint)
                    .ifPresent(constraints::add);
        }
        return constraints;
    }

    private static ConstraintDecision evaluate(
            Constraint constraint, RoutingContext context, List<ConstraintEvaluator> evaluators) {
        for (ConstraintEvaluator evaluator : evaluators) {
            if (evaluator.supports(constraint)) {
                return evaluator.evaluate(constraint, context);
            }
        }
        return ConstraintDecision.UNRESOLVED;
    }

    private static int rank(ConstraintDecision d) {
        return switch (d) {
            case BLOCK -> 3;
            case REQUIRE_CONFIRMATION -> 2;
            case UNRESOLVED -> 1;
            case ALLOW -> 0;
        };
    }
}
