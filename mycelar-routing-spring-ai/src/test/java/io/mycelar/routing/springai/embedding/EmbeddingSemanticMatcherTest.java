package io.mycelar.routing.springai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mycelar.core.graph.GraphNode;
import io.mycelar.routing.springai.exception.EmbeddingDimensionMismatchException;
import io.mycelar.routing.springai.exception.RoutingEmbeddingException;
import io.mycelar.routing.springai.fixture.MapEmbeddingModel;
import io.mycelar.routing.springai.fixture.Nodes;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingSemanticMatcherTest {

    private final NodeRoutingTextEncoder encoder = new DefaultNodeRoutingTextEncoder();
    private final RoutingTextFingerprint fingerprint = new Sha256RoutingTextFingerprint();
    private final VectorSimilarity similarity = new CosineSimilarity();

    @Test
    void draftOutranksUpdateForPolishParaphrase() {
        GraphNode draft =
                Nodes.node(
                        "draft",
                        "draft-email",
                        "Prepare an email or follow-up without sending it.",
                        List.of(),
                        List.of("write them an email", "napisz mu maila"));
        GraphNode update =
                Nodes.node(
                        "update",
                        "update",
                        "Modify CRM lead fields or properties.",
                        List.of(),
                        List.of("change their status"));
        String draftText = encoder.encode(draft);
        String updateText = encoder.encode(update);
        MapEmbeddingModel model =
                new MapEmbeddingModel()
                        .put("napisz mu maila", 1f, 0f)
                        .put(draftText, 0.95f, 0.05f)
                        .put(updateText, 0f, 1f);
        EmbeddingSemanticMatcher matcher = matcher(model, EmbeddingFailureMode.IGNORE_SIGNAL);
        double draftScore = matcher.similarity("napisz mu maila", draft);
        double updateScore = matcher.similarity("napisz mu maila", update);
        assertThat(draftScore).isGreaterThan(updateScore);
        assertThat(draftScore).isGreaterThan(0.5);
        assertThat(model.calls()).isEqualTo(3);
    }

    @Test
    void cachesQueryAndProfileEmbeddings() {
        GraphNode draft = Nodes.node("draft", "draft", "Prepare an email.", List.of(), List.of());
        String text = encoder.encode(draft);
        MapEmbeddingModel model = new MapEmbeddingModel().put("hello", 1f, 0f).put(text, 1f, 0f);
        EmbeddingSemanticMatcher matcher = matcher(model, EmbeddingFailureMode.IGNORE_SIGNAL);
        matcher.similarity("hello", draft);
        matcher.similarity("hello", draft);
        assertThat(model.calls()).isEqualTo(2);
        assertThat(model.batchSizes()).containsExactly(1, 1);
    }

    @Test
    void reembedsWhenMetadataChanges() {
        GraphNode first = Nodes.node("draft", "draft", "write email", List.of(), List.of());
        GraphNode second = Nodes.node("draft", "draft", "prepare email without sending", List.of(), List.of());
        MapEmbeddingModel model =
                new MapEmbeddingModel()
                        .put("q", 1f, 0f)
                        .put(encoder.encode(first), 1f, 0f)
                        .put(encoder.encode(second), 0f, 1f);
        EmbeddingSemanticMatcher matcher = matcher(model, EmbeddingFailureMode.IGNORE_SIGNAL);
        matcher.similarity("q", first);
        matcher.similarity("q", second);
        assertThat(model.calls()).isEqualTo(3);
    }

    @Test
    void ignoreSignalReturnsNeutralOnEmbedderFailure() {
        MapEmbeddingModel model = new MapEmbeddingModel().failWith(new RuntimeException("down"));
        EmbeddingSemanticMatcher matcher = matcher(model, EmbeddingFailureMode.IGNORE_SIGNAL);
        GraphNode node = Nodes.node("n", "n", "n", List.of(), List.of());
        assertThat(matcher.similarity("q", node)).isEqualTo(0.5);
        assertThat(matcher.similarity("q", node)).isEqualTo(0.5);
        assertThat(model.calls()).isEqualTo(1);
    }

    @Test
    void failRoutingThrowsAndRetriesNextCall() {
        MapEmbeddingModel model = new MapEmbeddingModel().failWith(new RuntimeException("down"));
        EmbeddingSemanticMatcher matcher = matcher(model, EmbeddingFailureMode.FAIL_ROUTING);
        GraphNode node = Nodes.node("n", "n", "n", List.of(), List.of());
        assertThatThrownBy(() -> matcher.similarity("q", node))
                .isInstanceOf(RoutingEmbeddingException.class)
                .hasCauseInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> matcher.similarity("q", node)).isInstanceOf(RoutingEmbeddingException.class);
        assertThat(model.calls()).isEqualTo(2);
    }

    @Test
    void dimensionMismatchIsNotFailSoft() {
        GraphNode node = Nodes.node("n", "n", "desc", List.of(), List.of());
        MapEmbeddingModel model =
                new MapEmbeddingModel().put("q", 1f, 0f).put(encoder.encode(node), 1f);
        EmbeddingSemanticMatcher matcher = matcher(model, EmbeddingFailureMode.IGNORE_SIGNAL);
        assertThatThrownBy(() -> matcher.similarity("q", node))
                .isInstanceOf(EmbeddingDimensionMismatchException.class);
    }

    @Test
    void clearQueryCacheForcesReembed() {
        GraphNode draft = Nodes.node("draft", "draft", "Prepare an email.", List.of(), List.of());
        String text = encoder.encode(draft);
        MapEmbeddingModel model = new MapEmbeddingModel().put("hello", 1f, 0f).put(text, 1f, 0f);
        EmbeddingSemanticMatcher matcher = matcher(model, EmbeddingFailureMode.IGNORE_SIGNAL);
        matcher.similarity("hello", draft);
        assertThat(model.calls()).isEqualTo(2);
        matcher.clearQueryCache();
        matcher.similarity("hello", draft);
        assertThat(model.calls()).isEqualTo(3);
    }

    private EmbeddingSemanticMatcher matcher(MapEmbeddingModel model, EmbeddingFailureMode mode) {
        return new EmbeddingSemanticMatcher(
                model,
                new InMemoryRoutingEmbeddingIndex(),
                encoder,
                fingerprint,
                similarity,
                true,
                mode);
    }
}
