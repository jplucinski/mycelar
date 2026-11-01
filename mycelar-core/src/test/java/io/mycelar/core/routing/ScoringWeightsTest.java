package io.mycelar.core.routing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ScoringWeightsTest {

    @Test
    void negativeWeightIsIae() {
        assertThatThrownBy(() -> new ScoringWeights(-0.1, 0.0, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nanWeightIsIae() {
        assertThatThrownBy(() -> new ScoringWeights(Double.NaN, 0.0, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativePathPenaltyAlphaIsIae() {
        assertThatThrownBy(() -> new ScoringWeights(0.0, 0.0, 0.0, -0.01))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
