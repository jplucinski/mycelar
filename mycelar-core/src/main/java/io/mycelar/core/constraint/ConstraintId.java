package io.mycelar.core.constraint;

import java.util.Objects;

public record ConstraintId(String value) {

    public ConstraintId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ConstraintId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
