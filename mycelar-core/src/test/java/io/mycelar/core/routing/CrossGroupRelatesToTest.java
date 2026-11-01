package io.mycelar.core.routing;

import static org.assertj.core.api.Assertions.assertThat;

import io.mycelar.core.capability.CapabilityDescriptor;
import io.mycelar.core.capability.CapabilityId;
import io.mycelar.core.capability.CapabilityResolver;
import io.mycelar.core.capability.CapabilityScope;
import io.mycelar.core.capability.GatewayId;
import io.mycelar.core.constraint.Constraint;
import io.mycelar.core.constraint.ConstraintDecision;
import io.mycelar.core.constraint.ConstraintEvaluator;
import io.mycelar.core.constraint.ConstraintId;
import io.mycelar.core.context.CapsuleType;
import io.mycelar.core.context.ContextCapsule;
import io.mycelar.core.graph.CapsuleId;
import io.mycelar.core.graph.EdgeId;
import io.mycelar.core.graph.EdgeMetadata;
import io.mycelar.core.graph.EdgeType;
import io.mycelar.core.graph.GraphEdge;
import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.MycelarGraph;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.graph.NodeMetadata;
import io.mycelar.core.graph.NodeType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossGroupRelatesToTest {

    private static final CapabilityId READ_ALPHA = new CapabilityId("read-alpha");
    private static final CapabilityId READ_BETA = new CapabilityId("read-beta");
    private static final CapabilityId WRITE_GAMMA = new CapabilityId("write-gamma");
    private static final CapabilityId WRITE_DELTA = new CapabilityId("write-delta");

    private final MycelarGraph graph = graph();
    private final NeedsOpenEvaluator evaluator = new NeedsOpenEvaluator();

    @Test
    void pathThroughBothGroupsResolvesBothGroups() {
        RoutePath bothGroups =
                new RoutePath(
                        List.of(
                                new NodeId("root"),
                                new NodeId("resource"),
                                new NodeId("read-group"),
                                new NodeId("write-group")),
                        List.of(
                                new EdgeId("root-resource"),
                                new EdgeId("resource-read"),
                                new EdgeId("read-write")));

        CapabilityScope blocked =
                new CapabilityResolver()
                        .resolve(graph, bothGroups, RoutingContext.empty(), List.of(evaluator));
        assertThat(blocked.allowed()).containsExactlyInAnyOrder(READ_ALPHA, READ_BETA, WRITE_GAMMA);
        assertThat(blocked.blocked())
                .containsExactly(
                        new io.mycelar.core.capability.BlockedCapability(
                                WRITE_DELTA, ConstraintDecision.BLOCK, List.of(new ConstraintId("hold"))));

        CapabilityScope open =
                new CapabilityResolver()
                        .resolve(
                                graph,
                                bothGroups,
                                RoutingContext.of(Map.of("open", true)),
                                List.of(evaluator));
        assertThat(open.allowed())
                .containsExactlyInAnyOrder(READ_ALPHA, READ_BETA, WRITE_GAMMA, WRITE_DELTA);
        assertThat(open.blocked()).isEmpty();
    }

    @Test
    void siblingGroupsMeetOnlyThroughRelatesTo() {
        RouteResult all =
                runtime()
                        .route(
                                RouteRequest.builder()
                                        .intent("inspect alpha")
                                        .context(RoutingContext.empty())
                                        .topK(100)
                                        .build());

        assertThat(all.candidates()).isNotEmpty();
        assertThat(all.candidates())
                .allSatisfy(
                        candidate -> {
                            assertThat(candidate.path().edgeIds()).doesNotContain(new EdgeId("delta-hold"));
                            if (candidate.path().nodeIds().contains(new NodeId("hold"))) {
                                assertThat(candidate.path().nodeIds()).containsExactly(new NodeId("hold"));
                            }
                        });

        List<RouteCandidate> both =
                all.candidates().stream()
                        .filter(candidate -> contains(candidate, "read-group") && contains(candidate, "write-group"))
                        .toList();
        assertThat(both).isNotEmpty();
        assertThat(both)
                .allSatisfy(
                        candidate -> assertThat(candidate.path().edgeIds()).contains(new EdgeId("read-write")));
    }

    @Test
    void readOnlyIntentAtTopOneStaysOnReadPath() {
        RouteResult result =
                runtime()
                        .route(
                                RouteRequest.builder()
                                        .intent("inspect alpha")
                                        .context(RoutingContext.empty())
                                        .topK(1)
                                        .build());

        RouteCandidate primary = result.primary().orElseThrow();
        assertThat(primary.path().nodeIds())
                .extracting(NodeId::value)
                .contains("read-group", "read-alpha")
                .doesNotContain("write-group");
        assertThat(primary.capabilityScope().allowed()).containsExactlyInAnyOrder(READ_ALPHA, READ_BETA);
        assertThat(primary.capabilityScope().allowed()).doesNotContain(WRITE_GAMMA, WRITE_DELTA);
        assertThat(primary.capabilityScope().blocked())
                .extracting(block -> block.capabilityId())
                .doesNotContain(WRITE_GAMMA, WRITE_DELTA);
    }

    @Test
    void dualIntentTopTwoKeepsPrimaryCone() {
        RouteResult result =
                runtime()
                        .route(
                                RouteRequest.builder()
                                        .intent("inspect alpha mutate gamma")
                                        .context(RoutingContext.empty())
                                        .topK(2)
                                        .build());

        assertThat(result.candidates()).hasSizeGreaterThan(1);
        assertThat(result.candidates()).anyMatch(candidate -> contains(candidate, "read-group"));
        assertThat(result.candidates()).anyMatch(candidate -> contains(candidate, "write-group"));

        RouteCandidate primary = result.primary().orElseThrow();
        assertThat(primary.path().nodeIds())
                .extracting(NodeId::value)
                .containsExactly("root", "resource", "read-group", "read-alpha");
        assertThat(primary.capabilityScope().allowed()).containsExactlyInAnyOrder(READ_ALPHA, READ_BETA);
        assertThat(primary.capabilityScope().allowed()).doesNotContain(WRITE_GAMMA, WRITE_DELTA);
        assertThat(primary.contextStack().capsules())
                .extracting(resolved -> resolved.capsule().id().value())
                .containsExactly("read-note");

        RouteResult open =
                runtime()
                        .route(
                                RouteRequest.builder()
                                        .intent("inspect alpha mutate gamma")
                                        .context(RoutingContext.of(Map.of("open", true)))
                                        .topK(2)
                                        .build());
        RouteCandidate opened = open.primary().orElseThrow();
        assertThat(opened.path().nodeIds())
                .extracting(NodeId::value)
                .containsExactly("root", "resource", "read-group", "read-alpha");
        assertThat(opened.capabilityScope().allowed()).doesNotContain(WRITE_DELTA);
    }

    private MycelarRuntime runtime() {
        return MycelarRuntime.builder().graph(graph).constraintEvaluators(List.of(evaluator)).build();
    }

    private static boolean contains(RouteCandidate candidate, String nodeId) {
        return candidate.path().nodeIds().contains(new NodeId(nodeId));
    }

    private static MycelarGraph graph() {
        return MycelarGraph.builder()
                .node(node("root", NodeType.DOMAIN, "root", List.of(), List.of()))
                .node(node("resource", NodeType.RESOURCE, "resource", List.of(), List.of()))
                .node(
                        node(
                                "read-group",
                                NodeType.CONTEXT,
                                "read",
                                List.of("inspect"),
                                List.of(capsule("read-note", "Read group note."))))
                .node(
                        node(
                                "write-group",
                                NodeType.CONTEXT,
                                "write",
                                List.of("mutate"),
                                List.of(capsule("write-note", "Write group note."))))
                .node(capability("read-alpha", "read alpha", List.of("inspect", "alpha")))
                .node(capability("read-beta", "read beta", List.of("inspect", "beta")))
                .node(capability("write-gamma", "write gamma", List.of("mutate", "gamma")))
                .node(capability("write-delta", "write delta", List.of("mutate", "delta")))
                .node(constraint("hold", "needs-open"))
                .edge(contains("root-resource", "root", "resource"))
                .edge(contains("resource-read", "resource", "read-group"))
                .edge(contains("resource-write", "resource", "write-group"))
                .edge(contains("read-alpha-edge", "read-group", "read-alpha"))
                .edge(contains("read-beta-edge", "read-group", "read-beta"))
                .edge(contains("write-gamma-edge", "write-group", "write-gamma"))
                .edge(contains("write-delta-edge", "write-group", "write-delta"))
                .edge(relates("read-write", "read-group", "write-group"))
                .edge(requires("delta-hold", "write-delta", "hold"))
                .build();
    }

    private static GraphNode node(
            String id,
            NodeType type,
            String label,
            List<String> keywords,
            List<ContextCapsule> capsules) {
        return new GraphNode(
                new NodeId(id),
                type,
                new NodeMetadata(label, "", List.of(), keywords, Map.of()),
                capsules,
                null,
                null);
    }

    private static GraphNode capability(String id, String label, List<String> keywords) {
        return new GraphNode(
                new NodeId(id),
                NodeType.CAPABILITY,
                new NodeMetadata(label, "", List.of(), keywords, Map.of()),
                List.of(),
                new CapabilityDescriptor(
                        new CapabilityId(id), id, "", new GatewayId("gateway"), id, Map.of()),
                null);
    }

    private static GraphNode constraint(String id, String type) {
        return new GraphNode(
                new NodeId(id),
                NodeType.CONSTRAINT,
                new NodeMetadata(id, "", List.of(), List.of(), Map.of()),
                List.of(),
                null,
                new Constraint(new ConstraintId(id), type, type, Map.of()));
    }

    private static ContextCapsule capsule(String id, String text) {
        return new ContextCapsule(new CapsuleId(id), CapsuleType.DOMAIN_CONTEXT, text, 1, 1.0, null);
    }

    private static GraphEdge contains(String edgeId, String source, String target) {
        return edge(edgeId, source, target, EdgeType.CONTAINS);
    }

    private static GraphEdge relates(String edgeId, String source, String target) {
        return edge(edgeId, source, target, EdgeType.RELATES_TO);
    }

    private static GraphEdge requires(String edgeId, String source, String target) {
        return edge(edgeId, source, target, EdgeType.REQUIRES);
    }

    private static GraphEdge edge(String edgeId, String source, String target, EdgeType type) {
        return new GraphEdge(
                new EdgeId(edgeId),
                new NodeId(source),
                new NodeId(target),
                type,
                1.0,
                new EdgeMetadata(Map.of()));
    }

    private static final class NeedsOpenEvaluator implements ConstraintEvaluator {
        @Override
        public boolean supports(Constraint constraint) {
            return "needs-open".equals(constraint.type());
        }

        @Override
        public ConstraintDecision evaluate(Constraint constraint, RoutingContext context) {
            return context.get("open").filter(Boolean.TRUE::equals).isPresent()
                    ? ConstraintDecision.ALLOW
                    : ConstraintDecision.BLOCK;
        }
    }
}
