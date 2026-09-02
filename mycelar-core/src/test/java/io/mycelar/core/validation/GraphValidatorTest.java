package io.mycelar.core.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mycelar.core.graph.EdgeId;
import io.mycelar.core.graph.EdgeMetadata;
import io.mycelar.core.graph.EdgeType;
import io.mycelar.core.graph.GraphEdge;
import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.graph.NodeMetadata;
import io.mycelar.core.graph.NodeType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphValidatorTest {

    private final GraphValidator validator = new GraphValidator();

    private GraphNode domain(String id) {
        return new GraphNode(
                new NodeId(id),
                NodeType.DOMAIN,
                new NodeMetadata(id, "", List.of(), List.of(), Map.of()),
                List.of(),
                Optional.empty(),
                Optional.empty());
    }

    @Test
    void duplicateNodeIdsFail() {
        GraphNode a = domain("crm");
        assertThatThrownBy(() -> validator.validate(List.of(a, a), List.of()))
                .isInstanceOf(GraphValidationException.class);
    }

    @Test
    void requiresMustTargetConstraint() {
        GraphNode cap =
                new GraphNode(
                        new NodeId("send"),
                        NodeType.CAPABILITY,
                        new NodeMetadata("send", "", List.of(), List.of(), Map.of()),
                        List.of(),
                        Optional.of(
                                new io.mycelar.core.capability.CapabilityDescriptor(
                                        new io.mycelar.core.capability.CapabilityId("send"),
                                        "send",
                                        "",
                                        new io.mycelar.core.capability.GatewayId("crm-mcp"),
                                        "send_message",
                                        Map.of())),
                        Optional.empty());
        GraphNode leads = domain("leads");
        GraphEdge requires =
                new GraphEdge(
                        new EdgeId("r1"),
                        new NodeId("send"),
                        new NodeId("leads"),
                        EdgeType.REQUIRES,
                        1.0,
                        new EdgeMetadata(Map.of()));
        assertThatThrownBy(() -> validator.validate(List.of(cap, leads), List.of(requires)))
                .isInstanceOf(GraphValidationException.class);
    }

    @Test
    void containsToConstraintFails() {
        GraphNode crm = domain("crm");
        GraphNode constraint =
                new GraphNode(
                        new NodeId("confirm"),
                        NodeType.CONSTRAINT,
                        new NodeMetadata("confirm", "", List.of(), List.of(), Map.of()),
                        List.of(),
                        Optional.empty(),
                        Optional.of(
                                new io.mycelar.core.constraint.Constraint(
                                        new io.mycelar.core.constraint.ConstraintId("confirm"),
                                        "confirm-before-send",
                                        "need confirm",
                                        Map.of())));
        GraphEdge contains =
                new GraphEdge(
                        new EdgeId("e1"),
                        new NodeId("crm"),
                        new NodeId("confirm"),
                        EdgeType.CONTAINS,
                        1.0,
                        new EdgeMetadata(Map.of()));
        assertThatThrownBy(() -> validator.validate(List.of(crm, constraint), List.of(contains)))
                .isInstanceOf(GraphValidationException.class);
    }
}
