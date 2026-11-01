package io.mycelar.core.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultRouteScorerTest {

    @Test
    void heavierPathBeatsLighterWhenLexiconEqual() {
        GraphNode root =
                new GraphNode(
                        new NodeId("root"),
                        NodeType.DOMAIN,
                        new NodeMetadata("follow-up", "follow-up", List.of(), List.of(), Map.of()),
                        List.of(),
                        null,
                        null);
        GraphNode a =
                new GraphNode(
                        new NodeId("a"),
                        NodeType.CONTEXT,
                        new NodeMetadata("follow-up", "follow-up", List.of(), List.of(), Map.of()),
                        List.of(),
                        null,
                        null);
        GraphNode b =
                new GraphNode(
                        new NodeId("b"),
                        NodeType.CONTEXT,
                        new NodeMetadata("follow-up", "follow-up", List.of(), List.of(), Map.of()),
                        List.of(),
                        null,
                        null);
        MycelarGraph graph =
                MycelarGraph.builder()
                        .node(root)
                        .node(a)
                        .node(b)
                        .edge(new GraphEdge(new EdgeId("ra"), new NodeId("root"), new NodeId("a"), EdgeType.RELATES_TO, 0.9, new EdgeMetadata(Map.of())))
                        .edge(new GraphEdge(new EdgeId("rb"), new NodeId("root"), new NodeId("b"), EdgeType.RELATES_TO, 0.4, new EdgeMetadata(Map.of())))
                        .build();
        DefaultRouteScorer scorer =
                new DefaultRouteScorer(
                        new LexicalSemanticMatcher(),
                        ScoringWeights.defaults(),
                        RoutingLimits.defaults());
        RouteRequest req = RouteRequest.builder().intent("follow-up").context(RoutingContext.empty()).topK(2).build();
        RouteScore sa = scorer.score(req, new RoutePath(List.of(new NodeId("root"), new NodeId("a")), List.of(new EdgeId("ra"))), graph);
        RouteScore sb = scorer.score(req, new RoutePath(List.of(new NodeId("root"), new NodeId("b")), List.of(new EdgeId("rb"))), graph);
        assertThat(sa.total()).isGreaterThan(sb.total());
        assertThat(sa.componentMap().keySet())
                .containsExactlyInAnyOrder("semantic", "topology", "runtimeContext", "pathPenalty");
    }

    @Test
    void emptyContextScoresNeutral() {
        RouteScore score = score(RoutingContext.empty(), (intent, node) -> 0.0, RoutingLimits.defaults(), path("root"));
        assertThat(score.components().runtimeContext()).isEqualTo(0.5);
    }

    @Test
    void attributeEqualityHitsContext() {
        GraphNode root = node("root", Map.of("tenant", "acme"), "other");
        RouteScore score =
                score(
                        graph(root),
                        RoutingContext.of(Map.of("tenant", "acme")),
                        (intent, node) -> 0.0,
                        RoutingLimits.defaults(),
                        path("root"));
        assertThat(score.components().runtimeContext()).isEqualTo(1.0);
    }

    @Test
    void tokenHitsContext() {
        GraphNode root = node("root", Map.of(), "Acme leads");
        RouteScore score =
                score(
                        graph(root),
                        RoutingContext.of(Map.of("note", "acme")),
                        (intent, node) -> 0.0,
                        RoutingLimits.defaults(),
                        path("root"));
        assertThat(score.components().runtimeContext()).isEqualTo(1.0);
    }

    @Test
    void nonFiniteSimilarityIsIae() {
        assertThatThrownBy(
                        () ->
                                score(
                                        RoutingContext.empty(),
                                        (intent, node) -> Double.NaN,
                                        RoutingLimits.defaults(),
                                        path("root")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownNodeIsIae() {
        assertThatThrownBy(
                        () ->
                                score(
                                        RoutingContext.empty(),
                                        (intent, node) -> 0.0,
                                        RoutingLimits.defaults(),
                                        new RoutePath(List.of(new NodeId("missing")), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown node");
    }

    @Test
    void unknownEdgeIsIae() {
        GraphNode root = node("root", Map.of(), "root");
        GraphNode child = node("child", Map.of(), "child");
        MycelarGraph graph = MycelarGraph.builder().node(root).node(child).build();
        RoutePath path =
                new RoutePath(List.of(new NodeId("root"), new NodeId("child")), List.of(new EdgeId("missing")));
        assertThatThrownBy(
                        () ->
                                score(
                                        graph,
                                        RoutingContext.empty(),
                                        (intent, node) -> 0.0,
                                        RoutingLimits.defaults(),
                                        path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown edge");
    }

    @Test
    void zeroMaxDepthHasNoPathPenalty() {
        GraphNode root = node("root", Map.of(), "root");
        GraphNode child = node("child", Map.of(), "child");
        MycelarGraph graph =
                MycelarGraph.builder()
                        .node(root)
                        .node(child)
                        .edge(
                                new GraphEdge(
                                        new EdgeId("e"),
                                        new NodeId("root"),
                                        new NodeId("child"),
                                        EdgeType.RELATES_TO,
                                        1.0,
                                        new EdgeMetadata(Map.of())))
                        .build();
        RoutePath path =
                new RoutePath(List.of(new NodeId("root"), new NodeId("child")), List.of(new EdgeId("e")));
        RouteScore score =
                score(graph, RoutingContext.empty(), (intent, node) -> 0.0, new RoutingLimits(0, 1), path);
        assertThat(score.components().pathPenalty()).isZero();
    }

    @Test
    void similarityIsClampedToUnitInterval() {
        RouteScore high =
                score(RoutingContext.empty(), (intent, node) -> 2.0, RoutingLimits.defaults(), path("root"));
        RouteScore low =
                score(RoutingContext.empty(), (intent, node) -> -1.0, RoutingLimits.defaults(), path("root"));
        assertThat(high.components().semantic()).isEqualTo(1.0);
        assertThat(low.components().semantic()).isEqualTo(0.0);
    }

    private static RouteScore score(
            RoutingContext context, SemanticMatcher matcher, RoutingLimits limits, RoutePath path) {
        return score(graph(node("root", Map.of(), "root")), context, matcher, limits, path);
    }

    private static RouteScore score(
            MycelarGraph graph,
            RoutingContext context,
            SemanticMatcher matcher,
            RoutingLimits limits,
            RoutePath path) {
        DefaultRouteScorer scorer = new DefaultRouteScorer(matcher, ScoringWeights.defaults(), limits);
        return scorer.score(RouteRequest.builder().intent("q").context(context).topK(1).build(), path, graph);
    }

    private static RoutePath path(String id) {
        return new RoutePath(List.of(new NodeId(id)), List.of());
    }

    private static MycelarGraph graph(GraphNode... nodes) {
        MycelarGraph.Builder builder = MycelarGraph.builder();
        for (GraphNode node : nodes) {
            builder.node(node);
        }
        return builder.build();
    }

    private static GraphNode node(String id, Map<String, Object> attributes, String label) {
        return new GraphNode(
                new NodeId(id),
                NodeType.DOMAIN,
                new NodeMetadata(label, "", List.of(), List.of(), attributes),
                List.of(),
                null,
                null);
    }
}
