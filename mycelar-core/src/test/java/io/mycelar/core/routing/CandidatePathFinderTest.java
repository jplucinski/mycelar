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

class CandidatePathFinderTest {

    private GraphNode ctx(String id) {
        return new GraphNode(
                new NodeId(id),
                NodeType.CONTEXT,
                new NodeMetadata(id, "", List.of(), List.of(), Map.of()),
                List.of(),
                null,
                null);
    }

    private GraphEdge relates(String id, String s, String t, double w) {
        return new GraphEdge(
                new EdgeId(id),
                new NodeId(s),
                new NodeId(t),
                EdgeType.RELATES_TO,
                w,
                new EdgeMetadata(Map.of()));
    }

    @Test
    void cycleDoesNotRepeatNodes() {
        MycelarGraph graph =
                MycelarGraph.builder()
                        .node(ctx("a"))
                        .node(ctx("b"))
                        .node(ctx("c"))
                        .edge(relates("ab", "a", "b", 1))
                        .edge(relates("bc", "b", "c", 1))
                        .edge(relates("ca", "c", "a", 1))
                        .build();
        List<RoutePath> paths =
                new CandidatePathFinder().find(graph, new RoutingLimits(8, 100));
        assertThat(paths)
                .allMatch(p -> p.nodeIds().size() == p.nodeIds().stream().distinct().count());
        assertThat(paths).anyMatch(p -> p.nodeIds().size() == 3);
    }

    @Test
    void capKeepsHeavyLeaf() {
        MycelarGraph.Builder b = MycelarGraph.builder().node(ctx("root"));
        for (int i = 0; i < 8; i++) {
            String id = "n" + i;
            b.node(ctx(id));
            b.edge(relates("e" + i, "root", id, i == 7 ? 1.0 : 0.1));
        }
        List<RoutePath> paths =
                new CandidatePathFinder().find(b.build(), new RoutingLimits(8, 4));
        assertThat(paths).hasSize(4);
        assertThat(paths.stream().map(RoutePath::nodeIds))
                .anyMatch(ids -> ids.equals(List.of(new NodeId("root"), new NodeId("n7"))));
        assertThat(paths.stream().anyMatch(p -> p.nodeIds().equals(List.of(new NodeId("root")))))
                .isTrue();
    }

    @Test
    void heavierParallelHopWins() {
        MycelarGraph graph =
                MycelarGraph.builder()
                        .node(ctx("root"))
                        .node(ctx("child"))
                        .edge(contains("light", "root", "child", 0.2))
                        .edge(relates("heavy", "root", "child", 0.9))
                        .build();
        List<RoutePath> paths = new CandidatePathFinder().find(graph, new RoutingLimits(2, 10));
        assertThat(paths)
                .filteredOn(p -> p.nodeIds().size() == 2)
                .extracting(p -> p.edgeIds().getFirst().value())
                .containsExactly("heavy");
    }

    @Test
    void containsBeatsRelatesToOnWeightTie() {
        MycelarGraph graph =
                MycelarGraph.builder()
                        .node(ctx("root"))
                        .node(ctx("child"))
                        .edge(relates("rel", "root", "child", 0.5))
                        .edge(contains("con", "root", "child", 0.5))
                        .build();
        List<RoutePath> paths = new CandidatePathFinder().find(graph, new RoutingLimits(2, 10));
        assertThat(paths)
                .filteredOn(p -> p.nodeIds().size() == 2)
                .extracting(p -> p.edgeIds().getFirst().value())
                .containsExactly("con");
    }

    @Test
    void maxDepthZeroKeepsSingleNodePaths() {
        MycelarGraph graph =
                MycelarGraph.builder()
                        .node(ctx("root"))
                        .node(ctx("child"))
                        .edge(relates("e", "root", "child", 1))
                        .build();
        List<RoutePath> paths = new CandidatePathFinder().find(graph, new RoutingLimits(0, 10));
        assertThat(paths).extracting(RoutePath::nodeIds).containsExactly(List.of(new NodeId("root")));
    }

    @Test
    void capKeepsFirstRootsByNodeIdWhenRootsExceedCap() {
        MycelarGraph graph =
                MycelarGraph.builder()
                        .node(ctx("c"))
                        .node(ctx("a"))
                        .node(ctx("b"))
                        .node(ctx("child"))
                        .edge(relates("ac", "a", "child", 1))
                        .build();
        List<RoutePath> paths = new CandidatePathFinder().find(graph, new RoutingLimits(4, 2));
        assertThat(paths).extracting(RoutePath::nodeIds).containsExactly(List.of(new NodeId("a")), List.of(new NodeId("b")));
    }

    @Test
    void zeroWeightPathLosesCapToHeavierPath() {
        MycelarGraph graph =
                MycelarGraph.builder()
                        .node(ctx("root"))
                        .node(ctx("zero"))
                        .node(ctx("heavy"))
                        .edge(relates("zero", "root", "zero", 0.0))
                        .edge(relates("heavy", "root", "heavy", 1.0))
                        .build();
        List<RoutePath> paths = new CandidatePathFinder().find(graph, new RoutingLimits(2, 2));
        assertThat(paths).extracting(RoutePath::edgeIds).containsExactly(List.of(), List.of(new EdgeId("heavy")));
    }

    private GraphEdge contains(String id, String s, String t, double w) {
        return new GraphEdge(
                new EdgeId(id),
                new NodeId(s),
                new NodeId(t),
                EdgeType.CONTAINS,
                w,
                new EdgeMetadata(Map.of()));
    }
}
