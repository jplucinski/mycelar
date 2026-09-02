package io.mycelar.core.graph;

import java.util.Objects;

public record CapsuleId(String value) {

    public CapsuleId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("CapsuleId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
