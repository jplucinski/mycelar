package io.mycelar.core.routing;

public record ScoringWeights(double semantic, double topology, double context, double pathPenaltyAlpha) {

    public static ScoringWeights defaults() {
        return new ScoringWeights(0.45, 0.35, 0.20, 0.05);
    }
}
