package io.mycelar.core.routing;

import static org.assertj.core.api.Assertions.assertThat;

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
}
