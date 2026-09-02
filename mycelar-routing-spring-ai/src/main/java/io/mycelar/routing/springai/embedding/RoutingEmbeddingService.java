package io.mycelar.routing.springai.embedding;

import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.NodeId;
import io.mycelar.routing.springai.exception.EmbeddingDimensionMismatchException;
import io.mycelar.routing.springai.exception.RoutingEmbeddingException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;

final class RoutingEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingEmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final RoutingEmbeddingIndex index;
    private final NodeRoutingTextEncoder encoder;
    private final RoutingTextFingerprint fingerprint;
    private final EmbeddingFailureMode failureMode;

    RoutingEmbeddingService(
            EmbeddingModel embeddingModel,
            RoutingEmbeddingIndex index,
            NodeRoutingTextEncoder encoder,
            RoutingTextFingerprint fingerprint,
            EmbeddingFailureMode failureMode) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        this.index = Objects.requireNonNull(index, "index");
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.failureMode = Objects.requireNonNull(failureMode, "failureMode");
    }

    EncodedNode encodeNode(GraphNode node) {
        Objects.requireNonNull(node, "node");
        String text = encoder.encode(node);
        return new EncodedNode(text, fingerprint.fingerprint(text));
    }

    boolean isFresh(NodeId id, String fingerprint) {
        return index.find(id).filter(existing -> existing.fingerprint().equals(fingerprint)).isPresent();
    }

    Optional<float[]> embedText(String text) {
        try {
            return Optional.of(embeddingModel.embed(text));
        } catch (RuntimeException e) {
            return handleEmbedFailure(e);
        }
    }

    Optional<List<float[]>> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return Optional.of(List.of());
        }
        try {
            return Optional.of(embeddingModel.embed(texts));
        } catch (RuntimeException e) {
            handleEmbedFailure(e);
            return Optional.empty();
        }
    }

    void cache(NodeId id, float[] vector, String fingerprint) {
        index.put(id, new RouteEmbedding(vector, fingerprint));
    }

    Optional<float[]> resolveNodeVector(GraphNode node) {
        EncodedNode encoded = encodeNode(node);
        Optional<RouteEmbedding> cached = index.find(node.id());
        if (cached.isPresent() && cached.get().fingerprint().equals(encoded.fingerprint())) {
            return Optional.of(cached.get().vector());
        }
        if (cached.isPresent()) {
            log.debug(
                    "MYCELAR_ROUTING_EMBEDDING_INVALIDATED candidate={} reason=profile_changed",
                    node.id().value());
        }
        Optional<float[]> embedded = embedText(encoded.text());
        if (embedded.isEmpty()) {
            return Optional.empty();
        }
        float[] vector = embedded.get();
        cache(node.id(), vector, encoded.fingerprint());
        return Optional.of(vector);
    }

    private <T> Optional<T> handleEmbedFailure(RuntimeException e) {
        if (e instanceof EmbeddingDimensionMismatchException) {
            throw e;
        }
        if (failureMode == EmbeddingFailureMode.FAIL_ROUTING) {
            throw new RoutingEmbeddingException("embedding failed", e);
        }
        log.warn("MYCELAR_ROUTING_EMBEDDING_FAILED mode={}", failureMode);
        return Optional.empty();
    }

    record EncodedNode(String text, String fingerprint) {}
}
