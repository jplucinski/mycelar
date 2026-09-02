package io.mycelar.routing.springai.embedding;

import io.mycelar.core.graph.NodeId;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class InMemoryRoutingEmbeddingIndex implements RoutingEmbeddingIndex {

    private final ConcurrentMap<NodeId, RouteEmbedding> embeddings = new ConcurrentHashMap<>();

    @Override
    public Optional<RouteEmbedding> find(NodeId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(embeddings.get(id));
    }

    @Override
    public void put(NodeId id, RouteEmbedding embedding) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(embedding, "embedding");
        embeddings.put(id, embedding);
    }

    @Override
    public void remove(NodeId id) {
        Objects.requireNonNull(id, "id");
        embeddings.remove(id);
    }

    @Override
    public void clear() {
        embeddings.clear();
    }
}
