package io.mycelar.core.routing;

public record RoutingLimits(int maxDepth, int maxCandidates) {

    public RoutingLimits {
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be >= 0");
        }
        if (maxCandidates < 1) {
            throw new IllegalArgumentException("maxCandidates must be >= 1");
        }
    }

    public static RoutingLimits defaults() {
        return new RoutingLimits(8, 100);
    }
}
