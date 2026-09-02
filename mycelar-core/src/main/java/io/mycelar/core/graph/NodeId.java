package io.mycelar.core.graph;

import java.util.Objects;

public record NodeId(String value) {

    public NodeId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("NodeId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
