package io.mycelar.routing.springai.embedding;

import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.MycelarGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.ai.embedding.EmbeddingModel;

public final class RoutingEmbeddingInitializer {

    private final RoutingEmbeddingService embeddingService;

    public RoutingEmbeddingInitializer(
            EmbeddingModel embeddingModel,
            RoutingEmbeddingIndex index,
            NodeRoutingTextEncoder encoder,
            RoutingTextFingerprint fingerprint,
            EmbeddingFailureMode failureMode) {
        this.embeddingService =
                new RoutingEmbeddingService(
                        embeddingModel, index, encoder, fingerprint, failureMode);
    }

    public void initialize(MycelarGraph graph) {
        Objects.requireNonNull(graph, "graph");
        List<GraphNode> misses = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<String> fingerprints = new ArrayList<>();
        for (GraphNode node : graph.nodes()) {
            RoutingEmbeddingService.EncodedNode encoded = embeddingService.encodeNode(node);
            if (!embeddingService.isFresh(node.id(), encoded.fingerprint())) {
                misses.add(node);
                texts.add(encoded.text());
                fingerprints.add(encoded.fingerprint());
            }
        }
        if (misses.isEmpty()) {
            return;
        }
        Optional<List<float[]>> vectors = embeddingService.embedBatch(texts);
        if (vectors.isEmpty()) {
            return;
        }
        List<float[]> resolved = vectors.get();
        for (int i = 0; i < misses.size(); i++) {
            embeddingService.cache(misses.get(i).id(), resolved.get(i), fingerprints.get(i));
        }
    }
}
