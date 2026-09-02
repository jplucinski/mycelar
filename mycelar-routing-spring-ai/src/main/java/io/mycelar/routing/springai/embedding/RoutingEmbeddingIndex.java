package io.mycelar.routing.springai.embedding;

import io.mycelar.core.graph.NodeId;
import java.util.Optional;

public interface RoutingEmbeddingIndex {
    Optional<RouteEmbedding> find(NodeId id);

    void put(NodeId id, RouteEmbedding embedding);

    void remove(NodeId id);

    void clear();

    static RoutingEmbeddingIndex inMemory() {
        return new InMemoryRoutingEmbeddingIndex();
    }
}
