package io.mycelar.core.routing;

import java.util.Map;
import java.util.Objects;

public record RouteScore(double total, Map<String, Double> components) {

    public RouteScore {
        Objects.requireNonNull(components, "components");
        components = Map.copyOf(components);
    }
}
