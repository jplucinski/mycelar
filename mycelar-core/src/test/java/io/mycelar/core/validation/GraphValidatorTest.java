package io.mycelar.core.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.mycelar.core.capability.CapabilityDescriptor;
import io.mycelar.core.capability.CapabilityId;
import io.mycelar.core.capability.GatewayId;
import io.mycelar.core.constraint.Constraint;
import io.mycelar.core.constraint.ConstraintId;
import io.mycelar.core.context.CapsuleType;
import io.mycelar.core.context.ContextCapsule;
import io.mycelar.core.graph.CapsuleId;
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
import org.junit.jupiter.api.Test;

class GraphValidatorTest {

    private final GraphValidator validator = new GraphValidator();

    private GraphNode domain(String id) {
        return new GraphNode(
                new NodeId(id),
                NodeType.DOMAIN,
                new NodeMetadata(id, "", List.of(), List.of(), Map.of()),
                List.of(),
                null,
                null);
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
                        new io.mycelar.core.capability.CapabilityDescriptor(
                                new io.mycelar.core.capability.CapabilityId("send"),
                                "send",
                                "",
                                new io.mycelar.core.capability.GatewayId("crm-mcp"),
                                "send_message",
                                Map.of()),
                        null);
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
                        null,
                        new io.mycelar.core.constraint.Constraint(
                                new io.mycelar.core.constraint.ConstraintId("confirm"),
                                "confirm-before-send",
                                "need confirm",
                                Map.of()));
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

    @Test
    void validGraphPasses() {
        validator.validate(
                List.of(domain("crm"), capability("send", "send"), constraint("confirm", "confirm")),
                List.of(edge("r1", "send", "confirm", EdgeType.REQUIRES)));
    }

    @Test
    void duplicateCapsuleIdsFail() {
        ContextCapsule shared = capsule("same");
        assertViolation(
                List.of(domainWith("a", shared), domainWith("b", shared)),
                List.of(),
                "nodes[b]",
                "duplicate CapsuleId: same");
    }

    @Test
    void duplicateCapabilityIdsFail() {
        assertViolation(
                List.of(capability("send-a", "send"), capability("send-b", "send")),
                List.of(),
                "nodes[send-b]",
                "duplicate CapabilityId: send");
    }

    @Test
    void duplicateConstraintIdsFail() {
        assertViolation(
                List.of(constraint("c1", "confirm"), constraint("c2", "confirm")),
                List.of(),
                "nodes[c2]",
                "duplicate ConstraintId: confirm");
    }

    @Test
    void duplicateEdgeIdsFail() {
        assertViolation(
                List.of(domain("a"), domain("b"), domain("c")),
                List.of(
                        edge("e1", "a", "b", EdgeType.CONTAINS),
                        edge("e1", "a", "c", EdgeType.RELATES_TO)),
                "edges[e1]",
                "duplicate EdgeId");
    }

    @Test
    void missingSourceFails() {
        assertViolation(
                List.of(domain("b")),
                List.of(edge("e1", "missing", "b", EdgeType.CONTAINS)),
                "edges[e1]",
                "missing source node: missing");
    }

    @Test
    void missingTargetFails() {
        assertViolation(
                List.of(domain("a")),
                List.of(edge("e1", "a", "missing", EdgeType.CONTAINS)),
                "edges[e1]",
                "missing target node: missing");
    }

    @Test
    void selfLoopFails() {
        assertViolation(
                List.of(domain("a")),
                List.of(edge("e1", "a", "a", EdgeType.RELATES_TO)),
                "edges[e1]",
                "self-loop not allowed");
    }

    @Test
    void duplicateTripleFails() {
        assertViolation(
                List.of(domain("a"), domain("b")),
                List.of(
                        edge("e1", "a", "b", EdgeType.CONTAINS),
                        edge("e2", "a", "b", EdgeType.CONTAINS)),
                "edges[e2]",
                "duplicate edge for (source, target, type)");
    }

    @Test
    void constraintMustNotHaveOutgoingEdges() {
        assertViolation(
                List.of(constraint("confirm", "confirm"), domain("crm")),
                List.of(edge("e1", "confirm", "crm", EdgeType.RELATES_TO)),
                "nodes[confirm]",
                "CONSTRAINT node must not have outgoing edges");
    }

    @Test
    void capabilityRequiresCapabilityPayload() {
        assertViolation(
                List.of(typed("send", NodeType.CAPABILITY, null, null)),
                List.of(),
                "nodes[send]",
                "CAPABILITY node requires capability");
    }

    @Test
    void capabilityMustNotCarryConstraint() {
        assertViolation(
                List.of(typed("send", NodeType.CAPABILITY, descriptor("send"), constraintOf("c"))),
                List.of(),
                "nodes[send]",
                "CAPABILITY node must not have constraint");
    }

    @Test
    void constraintRequiresConstraintPayload() {
        assertViolation(
                List.of(typed("confirm", NodeType.CONSTRAINT, null, null)),
                List.of(),
                "nodes[confirm]",
                "CONSTRAINT node requires constraint");
    }

    @Test
    void constraintMustNotCarryCapability() {
        assertViolation(
                List.of(
                        typed(
                                "confirm",
                                NodeType.CONSTRAINT,
                                descriptor("send"),
                                constraintOf("confirm"))),
                List.of(),
                "nodes[confirm]",
                "CONSTRAINT node must not have capability");
    }

    @Test
    void domainMustNotCarryCapability() {
        assertViolation(
                List.of(typed("crm", NodeType.DOMAIN, descriptor("send"), null)),
                List.of(),
                "nodes[crm]",
                "non-CAPABILITY node must not have capability");
    }

    @Test
    void domainMustNotCarryConstraint() {
        assertViolation(
                List.of(typed("crm", NodeType.DOMAIN, null, constraintOf("c"))),
                List.of(),
                "nodes[crm]",
                "non-CONSTRAINT node must not have constraint");
    }

    @Test
    void requiresSourceMustBeCapability() {
        assertViolation(
                List.of(domain("crm"), constraint("confirm", "confirm")),
                List.of(edge("r1", "crm", "confirm", EdgeType.REQUIRES)),
                "edges[r1]",
                "REQUIRES source must be CAPABILITY");
    }

    @Test
    void expandsToSourceMustNotBeConstraint() {
        assertViolation(
                List.of(constraint("confirm", "confirm"), domain("history")),
                List.of(edge("x1", "confirm", "history", EdgeType.EXPANDS_TO)),
                "edges[x1]",
                "EXPANDS_TO source must not be CONSTRAINT");
    }

    @Test
    void relatesToMustNotTouchConstraint() {
        assertViolation(
                List.of(domain("crm"), constraint("confirm", "confirm")),
                List.of(edge("e1", "crm", "confirm", EdgeType.RELATES_TO)),
                "edges[e1]",
                "RELATES_TO must not involve CONSTRAINT endpoints");
    }

    private void assertViolation(
            List<GraphNode> nodes, List<GraphEdge> edges, String path, String message) {
        GraphValidationException ex =
                catchThrowableOfType(
                        GraphValidationException.class, () -> validator.validate(nodes, edges));
        assertThat(ex).isNotNull();
        assertThat(ex.violations()).contains(new Violation(path, message));
    }

    private GraphNode domainWith(String id, ContextCapsule capsule) {
        return new GraphNode(
                new NodeId(id),
                NodeType.DOMAIN,
                meta(id),
                List.of(capsule),
                null,
                null);
    }

    private GraphNode capability(String nodeId, String capabilityId) {
        return typed(nodeId, NodeType.CAPABILITY, descriptor(capabilityId), null);
    }

    private GraphNode constraint(String nodeId, String constraintId) {
        return typed(nodeId, NodeType.CONSTRAINT, null, constraintOf(constraintId));
    }

    private GraphNode typed(
            String id, NodeType type, CapabilityDescriptor capability, Constraint constraint) {
        return new GraphNode(new NodeId(id), type, meta(id), List.of(), capability, constraint);
    }

    private static NodeMetadata meta(String id) {
        return new NodeMetadata(id, "", List.of(), List.of(), Map.of());
    }

    private static ContextCapsule capsule(String id) {
        return new ContextCapsule(new CapsuleId(id), CapsuleType.INSTRUCTION, id, 1, 1.0, null);
    }

    private static CapabilityDescriptor descriptor(String id) {
        return new CapabilityDescriptor(
                new CapabilityId(id), id, "", new GatewayId("gw"), id, Map.of());
    }

    private static Constraint constraintOf(String id) {
        return new Constraint(new ConstraintId(id), "rule", "", Map.of());
    }

    private static GraphEdge edge(String id, String source, String target, EdgeType type) {
        return new GraphEdge(
                new EdgeId(id),
                new NodeId(source),
                new NodeId(target),
                type,
                1.0,
                new EdgeMetadata(Map.of()));
    }
}
