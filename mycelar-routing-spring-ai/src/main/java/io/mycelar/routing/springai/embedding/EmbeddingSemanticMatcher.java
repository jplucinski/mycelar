package io.mycelar.routing.springai.embedding;

import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.routing.SemanticMatcher;
import io.mycelar.routing.springai.exception.EmbeddingDimensionMismatchException;
import io.mycelar.routing.springai.exception.RoutingEmbeddingException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.ai.embedding.EmbeddingModel;

public final class EmbeddingSemanticMatcher implements SemanticMatcher {

    private final RoutingEmbeddingService embeddingService;
    private final VectorSimilarity vectorSimilarity;
    private final boolean normalizeScore;
    private final ThreadLocal<Slot> querySlot = new ThreadLocal<>();

    public EmbeddingSemanticMatcher(
            EmbeddingModel embeddingModel,
            RoutingEmbeddingIndex index,
            NodeRoutingTextEncoder encoder,
            RoutingTextFingerprint fingerprint,
            VectorSimilarity vectorSimilarity,
            boolean normalizeScore,
            EmbeddingFailureMode failureMode) {
        this.embeddingService =
                new RoutingEmbeddingService(
                        embeddingModel, index, encoder, fingerprint, failureMode);
        this.vectorSimilarity = Objects.requireNonNull(vectorSimilarity, "vectorSimilarity");
        this.normalizeScore = normalizeScore;
    }

    @Override
    public double similarity(String intent, GraphNode node) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(node, "node");

        Slot slot = slotFor(intent);
        if (slot.ignored) {
            return 0.5;
        }

        float[] query = slot.query;
        if (query == null) {
            Optional<float[]> embedded = embedQuery(intent);
            if (embedded.isEmpty()) {
                return 0.5;
            }
            query = embedded.get();
            slot.query = query;
        }

        Optional<float[]> nodeVector = embeddingService.resolveNodeVector(node);
        if (nodeVector.isEmpty()) {
            slot.ignored = true;
            return 0.5;
        }

        if (query.length != nodeVector.get().length) {
            throw new EmbeddingDimensionMismatchException(
                    "query length "
                            + query.length
                            + " != candidate length "
                            + nodeVector.get().length);
        }

        double score = vectorSimilarity.similarity(query, nodeVector.get());
        if (!normalizeScore) {
            return score;
        }
        return clamp01((score + 1.0) / 2.0);
    }

    @Override
    public void clearQueryCache() {
        querySlot.remove();
    }

    private Slot slotFor(String intent) {
        Slot current = querySlot.get();
        if (current == null || !current.intent.equals(intent)) {
            current = new Slot(intent);
            querySlot.set(current);
        }
        return current;
    }

    private Optional<float[]> embedQuery(String text) {
        try {
            Optional<float[]> embedded = embeddingService.embedText(text);
            if (embedded.isEmpty()) {
                Slot slot = querySlot.get();
                if (slot != null) {
                    slot.ignored = true;
                }
            }
            return embedded;
        } catch (RoutingEmbeddingException e) {
            querySlot.remove();
            throw e;
        }
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private static final class Slot {
        private final String intent;
        private float[] query;
        private boolean ignored;

        private Slot(String intent) {
            this.intent = intent;
        }
    }
}
