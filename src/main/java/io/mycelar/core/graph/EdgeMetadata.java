package io.mycelar.core.graph;

import java.util.Map;
import java.util.Objects;

public record EdgeMetadata(Map<String, Object> attributes) {

    public EdgeMetadata {
        Objects.requireNonNull(attributes, "attributes");
        attributes = Map.copyOf(attributes);
    }
}
