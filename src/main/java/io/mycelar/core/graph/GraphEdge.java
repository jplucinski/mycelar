package io.mycelar.core.graph;

import java.util.Objects;

public record GraphEdge(
        EdgeId id, NodeId source, NodeId target, EdgeType type, double weight, EdgeMetadata metadata) {

    public GraphEdge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(metadata, "metadata");
        if (Double.isNaN(weight) || Double.isInfinite(weight) || weight < 0.0 || weight > 1.0) {
            throw new IllegalArgumentException("weight must be in [0,1]");
        }
    }
}
