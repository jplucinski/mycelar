package io.mycelar.core.routing;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RouteResult(List<RouteCandidate> candidates) {

    public RouteResult {
        Objects.requireNonNull(candidates, "candidates");
        candidates = List.copyOf(candidates);
    }

    public Optional<RouteCandidate> primary() {
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
    }
}
