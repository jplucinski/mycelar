package io.mycelar.core.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mycelar.core.context.ContextExpander;
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

class MycelarRuntimeTest {

    @Test
    void builderRejectsNullGraph() {
        assertThatThrownBy(() -> MycelarRuntime.builder().build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyGraphRoutesEmpty() {
        MycelarGraph graph = MycelarGraph.builder().build();
        RouteResult result =
                MycelarRuntime.builder().graph(graph).build().route("anything", RoutingContext.empty());
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void runtimeHasNoExpanderField() {
        assertThat(MycelarRuntime.class.getDeclaredFields())
                .noneMatch(f -> f.getType().equals(ContextExpander.class));
    }

    @Test
    void listsExpansionsWithoutCallingExpander() {
        GraphNode message =
                new GraphNode(
                        new NodeId("message"),
                        NodeType.CONTEXT,
                        new NodeMetadata("message", "message", List.of(), List.of(), Map.of()),
                        List.of(),
                        null,
                        null);
        GraphNode history =
                new GraphNode(
                        new NodeId("interaction-history"),
                        NodeType.CONTEXT,
                        new NodeMetadata("history", "", List.of(), List.of(), Map.of()),
                        List.of(),
                        null,
                        null);
        MycelarGraph graph =
                MycelarGraph.builder()
                        .node(message)
                        .node(history)
                        .edge(
                                new GraphEdge(
                                        new EdgeId("x1"),
                                        new NodeId("message"),
                                        new NodeId("interaction-history"),
                                        EdgeType.EXPANDS_TO,
                                        1.0,
                                        new EdgeMetadata(Map.of())))
                        .build();
        RouteResult result =
                MycelarRuntime.builder().graph(graph).build().route("message", RoutingContext.empty());
        assertThat(result.candidates()).isNotEmpty();
        assertThat(result.candidates().get(0).expansions())
                .extracting(e -> e.target().value())
                .contains("interaction-history");
    }

    @Test
    void blankIntentRejected() {
        MycelarGraph graph =
                MycelarGraph.builder()
                        .node(
                                new GraphNode(
                                        new NodeId("crm"),
                                        NodeType.DOMAIN,
                                        new NodeMetadata("crm", "", List.of(), List.of(), Map.of()),
                                        List.of(),
                                        null,
                                        null))
                        .build();
        assertThatThrownBy(
                        () ->
                                MycelarRuntime.builder()
                                        .graph(graph)
                                        .build()
                                        .route(RouteRequest.builder().intent("  ").topK(1).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonFiniteScoreIsIae() {
        MycelarGraph graph =
                MycelarGraph.builder()
                        .node(
                                new GraphNode(
                                        new NodeId("crm"),
                                        NodeType.DOMAIN,
                                        new NodeMetadata("crm", "", List.of(), List.of(), Map.of()),
                                        List.of(),
                                        null,
                                        null))
                        .build();
        RouteScorer nan =
                (req, path, g) ->
                        new RouteScore(
                                Double.NaN, new RouteScore.ScoreComponents(0.0, 0.0, 0.0, 0.0));
        assertThatThrownBy(
                        () ->
                                MycelarRuntime.builder()
                                        .graph(graph)
                                        .routeScorer(nan)
                                        .build()
                                        .route("crm", RoutingContext.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void graphReturnsSameInstance() {
        MycelarGraph graph = MycelarGraph.builder().build();
        MycelarRuntime runtime = MycelarRuntime.builder().graph(graph).build();
        assertThat(runtime.graph()).isSameAs(graph);
    }

    @Test
    void customMatcherChangesWinner() {
        GraphNode winner = labeled("winner");
        GraphNode loser = labeled("loser");
        MycelarGraph graph = MycelarGraph.builder().node(winner).node(loser).build();
        SemanticMatcher matcher = (intent, node) -> node.id().value().equals("winner") ? 1.0 : 0.0;
        RouteResult result =
                MycelarRuntime.builder()
                        .graph(graph)
                        .semanticMatcher(matcher)
                        .build()
                        .route("anything", RoutingContext.empty());
        assertThat(result.primary()).isPresent();
        assertThat(result.primary().orElseThrow().path().nodeIds()).containsExactly(new NodeId("winner"));
    }

    @Test
    void zeroDepthLimitStopsAtRoot() {
        GraphNode root = labeled("root");
        GraphNode child = labeled("child");
        MycelarGraph graph =
                MycelarGraph.builder()
                        .node(root)
                        .node(child)
                        .edge(
                                new GraphEdge(
                                        new EdgeId("e"),
                                        new NodeId("root"),
                                        new NodeId("child"),
                                        EdgeType.CONTAINS,
                                        1.0,
                                        new EdgeMetadata(Map.of())))
                        .build();
        RouteResult result =
                MycelarRuntime.builder()
                        .graph(graph)
                        .routingLimits(new RoutingLimits(0, 1))
                        .build()
                        .route("root", RoutingContext.empty());
        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().getFirst().path().nodeIds()).containsExactly(new NodeId("root"));
    }

    private static GraphNode labeled(String id) {
        return new GraphNode(
                new NodeId(id),
                NodeType.DOMAIN,
                new NodeMetadata(id, "", List.of(), List.of(), Map.of()),
                List.of(),
                null,
                null);
    }
}
