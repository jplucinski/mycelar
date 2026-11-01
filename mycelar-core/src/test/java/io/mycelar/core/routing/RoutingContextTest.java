package io.mycelar.core.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RoutingContextTest {

    @Test
    void nullAndEmptyMapsAreEmpty() {
        assertThat(RoutingContext.of(null).asMap()).isEmpty();
        assertThat(RoutingContext.of(Map.of()).asMap()).isEmpty();
    }
}
