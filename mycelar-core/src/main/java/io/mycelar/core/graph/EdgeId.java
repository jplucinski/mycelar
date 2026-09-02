package io.mycelar.core.graph;

import java.util.Objects;

public record EdgeId(String value) {

    public EdgeId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("EdgeId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
