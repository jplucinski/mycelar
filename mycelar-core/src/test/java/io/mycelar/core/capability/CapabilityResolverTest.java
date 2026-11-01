package io.mycelar.core.capability;

import static org.assertj.core.api.Assertions.assertThat;

import io.mycelar.core.constraint.Constraint;
import io.mycelar.core.constraint.ConstraintDecision;
import io.mycelar.core.constraint.ConstraintEvaluator;
import io.mycelar.core.constraint.ConstraintId;
import io.mycelar.core.graph.EdgeId;
import io.mycelar.core.graph.EdgeMetadata;
import io.mycelar.core.graph.EdgeType;
import io.mycelar.core.graph.GraphEdge;
import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.MycelarGraph;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.graph.NodeMetadata;
import io.mycelar.core.graph.NodeType;
import io.mycelar.core.routing.RoutePath;
import io.mycelar.core.routing.RoutingContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CapabilityResolverTest {

    private final CapabilityResolver resolver = new CapabilityResolver();

    @Test
    void coneIncludesDirectCapabilityChildrenOnly() {
        MycelarGraph graph = sampleGraph();
        CapabilityScope scope =
                resolver.resolve(
                        graph,
                        new RoutePath(
                                List.of(new NodeId("crm"), new NodeId("leads")),
                                List.of(new EdgeId("crm-leads"))),
                        RoutingContext.empty(),
                        List.of(new ConfirmEvaluator()));

        assertThat(scope.allowed()).containsExactly(new CapabilityId("search"));
        assertThat(scope.blocked()).isEmpty();
    }

    @Test
    void sendRequiresConfirmationUnlessConfirmed() {
        MycelarGraph graph = sampleGraph();
        RoutePath pathThroughSend =
                new RoutePath(
                        List.of(
                                new NodeId("crm"),
                                new NodeId("leads"),
                                new NodeId("message"),
                                new NodeId("send")),
                        List.of(
                                new EdgeId("crm-leads"),
                                new EdgeId("leads-message"),
                                new EdgeId("message-send")));

        CapabilityScope blocked =
                resolver.resolve(
                        graph, pathThroughSend, RoutingContext.empty(), List.of(new ConfirmEvaluator()));
        assertThat(blocked.allowed()).contains(new CapabilityId("search"));
        assertThat(blocked.blocked())
                .containsExactly(
                        new BlockedCapability(
                                new CapabilityId("send"),
                                ConstraintDecision.REQUIRE_CONFIRMATION,
                                List.of(new ConstraintId("confirm"))));

        CapabilityScope allowed =
                resolver.resolve(
                        graph,
                        pathThroughSend,
                        RoutingContext.of(Map.of("confirmed", true)),
                        List.of(new ConfirmEvaluator()));
        assertThat(allowed.allowed())
                .containsExactlyInAnyOrder(new CapabilityId("search"), new CapabilityId("send"));
        assertThat(allowed.blocked()).isEmpty();
    }

    @Test
    void missingEvaluatorYieldsUnresolved() {
        MycelarGraph graph = sampleGraph();
        CapabilityScope scope =
                resolver.resolve(
                        graph,
                        new RoutePath(
                                List.of(
                                        new NodeId("crm"),
                                        new NodeId("leads"),
                                        new NodeId("message"),
                                        new NodeId("send")),
                                List.of(
                                        new EdgeId("crm-leads"),
                                        new EdgeId("leads-message"),
                                        new EdgeId("message-send"))),
                        RoutingContext.empty(),
                        List.of());

        assertThat(scope.blocked())
                .containsExactly(
                        new BlockedCapability(
                                new CapabilityId("send"),
                                ConstraintDecision.UNRESOLVED,
                                List.of(new ConstraintId("confirm"))));
    }

    @Test
    void blockOutranksRequireConfirmation() {
        GraphNode crm = domain("crm");
        GraphNode cap =
                capability("ops", "ops", "run_ops");
        GraphNode blockConstraint =
                constraint("must-block", "always-block", "block it");
        GraphNode confirmConstraint =
                constraint("must-confirm", "confirm-before-send", "confirm it");
        MycelarGraph graph =
                MycelarGraph.builder()
                        .node(crm)
                        .node(cap)
                        .node(blockConstraint)
                        .node(confirmConstraint)
                        .edge(contains("crm-ops", "crm", "ops"))
                        .edge(requires("ops-block", "ops", "must-block"))
                        .edge(requires("ops-confirm", "ops", "must-confirm"))
                        .build();

        CapabilityScope scope =
                resolver.resolve(
                        graph,
                        new RoutePath(List.of(new NodeId("crm")), List.of()),
                        RoutingContext.empty(),
                        List.of(new AlwaysBlockEvaluator(), new ConfirmEvaluator()));

        assertThat(scope.allowed()).isEmpty();
        assertThat(scope.blocked())
                .containsExactly(
                        new BlockedCapability(
                                new CapabilityId("ops"),
                                ConstraintDecision.BLOCK,
                                List.of(
                                        new ConstraintId("must-block"),
                                        new ConstraintId("must-confirm"))));
    }

    @Test
    void missingPathNodeIsSkipped() {
        MycelarGraph graph = sampleGraph();
        CapabilityScope scope =
                resolver.resolve(
                        graph,
                        new RoutePath(
                                List.of(new NodeId("missing"), new NodeId("leads")),
                                List.of(new EdgeId("missing-edge"))),
                        RoutingContext.empty(),
                        List.of());
        assertThat(scope.allowed()).containsExactly(new CapabilityId("search"));
        assertThat(scope.blocked()).isEmpty();
    }

    private static MycelarGraph sampleGraph() {
        return MycelarGraph.builder()
                .node(domain("crm"))
                .node(domain("leads"))
                .node(domain("admin"))
                .node(domain("message"))
                .node(capability("search", "search", "search_leads"))
                .node(capability("delete-all", "delete-all", "delete_all"))
                .node(capability("send", "send", "send_message"))
                .node(constraint("confirm", "confirm-before-send", "confirm before send"))
                .edge(contains("crm-leads", "crm", "leads"))
                .edge(contains("crm-admin", "crm", "admin"))
                .edge(contains("leads-search", "leads", "search"))
                .edge(contains("leads-message", "leads", "message"))
                .edge(contains("admin-delete", "admin", "delete-all"))
                .edge(contains("message-send", "message", "send"))
                .edge(requires("send-confirm", "send", "confirm"))
                .build();
    }

    private static GraphNode domain(String id) {
        return new GraphNode(
                new NodeId(id),
                NodeType.DOMAIN,
                new NodeMetadata(id, "", List.of(), List.of(), Map.of()),
                List.of(),
                null,
                null);
    }

    private static GraphNode capability(String id, String name, String operationId) {
        return new GraphNode(
                new NodeId(id),
                NodeType.CAPABILITY,
                new NodeMetadata(name, "", List.of(), List.of(), Map.of()),
                List.of(),
                new CapabilityDescriptor(
                        new CapabilityId(id),
                        name,
                        "",
                        new GatewayId("crm-mcp"),
                        operationId,
                        Map.of()),
                null);
    }

    private static GraphNode constraint(String id, String type, String description) {
        return new GraphNode(
                new NodeId(id),
                NodeType.CONSTRAINT,
                new NodeMetadata(id, "", List.of(), List.of(), Map.of()),
                List.of(),
                null,
                new Constraint(new ConstraintId(id), type, description, Map.of()));
    }

    private static GraphEdge contains(String edgeId, String source, String target) {
        return new GraphEdge(
                new EdgeId(edgeId),
                new NodeId(source),
                new NodeId(target),
                EdgeType.CONTAINS,
                1.0,
                new EdgeMetadata(Map.of()));
    }

    private static GraphEdge requires(String edgeId, String source, String target) {
        return new GraphEdge(
                new EdgeId(edgeId),
                new NodeId(source),
                new NodeId(target),
                EdgeType.REQUIRES,
                1.0,
                new EdgeMetadata(Map.of()));
    }

    private static final class AlwaysBlockEvaluator implements ConstraintEvaluator {
        @Override
        public boolean supports(Constraint c) {
            return "always-block".equals(c.type());
        }

        @Override
        public ConstraintDecision evaluate(Constraint c, RoutingContext ctx) {
            return ConstraintDecision.BLOCK;
        }
    }
}

final class ConfirmEvaluator implements ConstraintEvaluator {
    @Override
    public boolean supports(Constraint c) {
        return "confirm-before-send".equals(c.type());
    }

    @Override
    public ConstraintDecision evaluate(Constraint c, RoutingContext ctx) {
        return ctx.get("confirmed").filter(Boolean.TRUE::equals).isPresent()
                ? ConstraintDecision.ALLOW
                : ConstraintDecision.REQUIRE_CONFIRMATION;
    }
}
