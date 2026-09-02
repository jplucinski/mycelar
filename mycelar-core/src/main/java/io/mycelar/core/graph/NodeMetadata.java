package io.mycelar.core.graph;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record NodeMetadata(
        String label,
        String description,
        List<String> aliases,
        List<String> keywords,
        Map<String, Object> attributes) {

    public NodeMetadata {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(aliases, "aliases");
        Objects.requireNonNull(keywords, "keywords");
        Objects.requireNonNull(attributes, "attributes");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        aliases = List.copyOf(aliases);
        keywords = List.copyOf(keywords);
        attributes = Map.copyOf(attributes);
    }
}
