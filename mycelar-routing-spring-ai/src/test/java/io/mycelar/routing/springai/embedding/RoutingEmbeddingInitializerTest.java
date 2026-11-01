package io.mycelar.routing.springai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mycelar.core.graph.EdgeId;
import io.mycelar.core.graph.EdgeMetadata;
import io.mycelar.core.graph.EdgeType;
import io.mycelar.core.graph.GraphEdge;
import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.MycelarGraph;
import io.mycelar.core.graph.NodeId;
import io.mycelar.routing.springai.exception.RoutingEmbeddingException;
import io.mycelar.routing.springai.fixture.MapEmbeddingModel;
import io.mycelar.routing.springai.fixture.Nodes;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoutingEmbeddingInitializerTest {

    @Test
    void batchEmbedsUncachedNodesInOneCall() {
        GraphNode a = Nodes.node("a", "a", "alpha", java.util.List.of(), java.util.List.of());
        GraphNode b = Nodes.node("b", "b", "beta", java.util.List.of(), java.util.List.of());
        MycelarGraph graph =
                MycelarGraph.builder()
                        .node(a)
                        .node(b)
                        .edge(
                                new GraphEdge(
                                        new EdgeId("ab"),
                                        new NodeId("a"),
                                        new NodeId("b"),
                                        EdgeType.CONTAINS,
                                        1.0,
                                        new EdgeMetadata(Map.of())))
                        .build();
        NodeRoutingTextEncoder encoder = new DefaultNodeRoutingTextEncoder();
        RoutingTextFingerprint fingerprint = new Sha256RoutingTextFingerprint();
        MapEmbeddingModel model =
                new MapEmbeddingModel().put(encoder.encode(a), 1f, 0f).put(encoder.encode(b), 0f, 1f);
        RoutingEmbeddingIndex index = new InMemoryRoutingEmbeddingIndex();
        var initializer =
                new RoutingEmbeddingInitializer(
                        model, index, encoder, fingerprint, EmbeddingFailureMode.FAIL_ROUTING);
        initializer.initialize(graph);
        assertThat(model.calls()).isEqualTo(1);
        assertThat(model.batchSizes()).containsExactly(2);
        assertThat(index.find(new NodeId("a"))).isPresent();
        assertThat(index.find(new NodeId("b"))).isPresent();
        initializer.initialize(graph);
        assertThat(model.calls()).isEqualTo(1);
    }

    @Test
    void ignoreSignalSkipsCacheWhenBatchEmbedFails() {
        MycelarGraph graph = twoNodeGraph();
        MapEmbeddingModel model = new MapEmbeddingModel().failWith(new RuntimeException("down"));
        RoutingEmbeddingIndex index = new InMemoryRoutingEmbeddingIndex();
        RoutingEmbeddingInitializer initializer =
                new RoutingEmbeddingInitializer(
                        model,
                        index,
                        new DefaultNodeRoutingTextEncoder(),
                        new Sha256RoutingTextFingerprint(),
                        EmbeddingFailureMode.IGNORE_SIGNAL);
        assertThatCode(() -> initializer.initialize(graph)).doesNotThrowAnyException();
        assertThat(index.find(new NodeId("a"))).isEmpty();
        assertThat(index.find(new NodeId("b"))).isEmpty();
    }

    @Test
    void failRoutingPropagatesBatchEmbedFailure() {
        MapEmbeddingModel model = new MapEmbeddingModel().failWith(new RuntimeException("down"));
        RoutingEmbeddingInitializer initializer =
                new RoutingEmbeddingInitializer(
                        model,
                        new InMemoryRoutingEmbeddingIndex(),
                        new DefaultNodeRoutingTextEncoder(),
                        new Sha256RoutingTextFingerprint(),
                        EmbeddingFailureMode.FAIL_ROUTING);
        assertThatThrownBy(() -> initializer.initialize(twoNodeGraph()))
                .isInstanceOf(RoutingEmbeddingException.class);
    }

    @Test
    void emptyBatchDoesNotCallTheModel() {
        MapEmbeddingModel model = new MapEmbeddingModel();
        RoutingEmbeddingService service =
                new RoutingEmbeddingService(
                        model,
                        new InMemoryRoutingEmbeddingIndex(),
                        new DefaultNodeRoutingTextEncoder(),
                        new Sha256RoutingTextFingerprint(),
                        EmbeddingFailureMode.FAIL_ROUTING);
        assertThat(service.embedBatch(java.util.List.of())).contains(java.util.List.of());
        assertThat(model.calls()).isZero();
    }

    private static MycelarGraph twoNodeGraph() {
        GraphNode a = Nodes.node("a", "a", "alpha", java.util.List.of(), java.util.List.of());
        GraphNode b = Nodes.node("b", "b", "beta", java.util.List.of(), java.util.List.of());
        return MycelarGraph.builder()
                .node(a)
                .node(b)
                .edge(
                        new GraphEdge(
                                new EdgeId("ab"),
                                new NodeId("a"),
                                new NodeId("b"),
                                EdgeType.CONTAINS,
                                1.0,
                                new EdgeMetadata(Map.of())))
                .build();
    }
}
