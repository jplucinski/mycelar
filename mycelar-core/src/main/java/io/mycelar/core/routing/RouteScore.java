package io.mycelar.core.routing;

import java.util.Map;
import java.util.Objects;

public record RouteScore(double total, ScoreComponents components) {

    public RouteScore {
        Objects.requireNonNull(components, "components");
    }

    public Map<String, Double> componentMap() {
        return components.asMap();
    }

    public record ScoreComponents(
            double semantic, double topology, double runtimeContext, double pathPenalty) {

        public Map<String, Double> asMap() {
            return Map.of(
                    "semantic", semantic,
                    "topology", topology,
                    "runtimeContext", runtimeContext,
                    "pathPenalty", pathPenalty);
        }
    }
}
