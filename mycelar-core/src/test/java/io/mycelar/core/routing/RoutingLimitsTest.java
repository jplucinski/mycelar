package io.mycelar.core.routing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RoutingLimitsTest {

    @Test
    void negativeMaxDepthIsIae() {
        assertThatThrownBy(() -> new RoutingLimits(-1, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroMaxCandidatesIsIae() {
        assertThatThrownBy(() -> new RoutingLimits(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
