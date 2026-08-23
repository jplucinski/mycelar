package io.mycelar.core.constraint;

import java.util.Map;
import java.util.Objects;

public record Constraint(
        ConstraintId id, String type, String description, Map<String, Object> attributes) {

    public Constraint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(attributes, "attributes");
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        attributes = Map.copyOf(attributes);
    }
}
