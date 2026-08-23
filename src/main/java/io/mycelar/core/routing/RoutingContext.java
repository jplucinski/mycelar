package io.mycelar.core.routing;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class RoutingContext {

    private final Map<String, Object> values;

    private RoutingContext(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new HashMap<>(values));
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

    public Object get(String key) {
        Objects.requireNonNull(key, "key");
        return values.get(key);
    }

    public Map<String, Object> asMap() {
        return values;
    }
}
