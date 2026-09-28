package io.mycelar.core.routing;

public record ScoringWeights(double semantic, double topology, double context, double pathPenaltyAlpha) {

    public ScoringWeights {
        validateWeight("semantic", semantic);
        validateWeight("topology", topology);
        validateWeight("context", context);
        if (Double.isNaN(pathPenaltyAlpha)
                || Double.isInfinite(pathPenaltyAlpha)
                || pathPenaltyAlpha < 0.0) {
            throw new IllegalArgumentException("pathPenaltyAlpha must be finite and >= 0");
        }
    }

    public static ScoringWeights defaults() {
        return new ScoringWeights(0.45, 0.35, 0.20, 0.05);
    }

    private static void validateWeight(String name, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " weight must be finite and >= 0");
        }
    }
}
