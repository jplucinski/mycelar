package io.mycelar.core.routing;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RoutingContext {

    private final Map<String, Object> values;

    private RoutingContext(Map<String, Object> values) {
        this.values = Map.copyOf(values);
    }

    public static RoutingContext empty() {
        return new RoutingContext(Map.of());
    }

    public static RoutingContext of(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return empty();
        }
        return new RoutingContext(values);
    }

    public Optional<Object> get(String key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(values.get(key));
    }

    public Map<String, Object> asMap() {
        return values;
    }
}
