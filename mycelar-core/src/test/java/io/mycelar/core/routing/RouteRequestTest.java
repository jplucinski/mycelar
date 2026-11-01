package io.mycelar.core.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RouteRequestTest {

    @Test
    void topKBelowOneIsIae() {
        assertThatThrownBy(() -> new RouteRequest("find leads", RoutingContext.empty(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullContextBecomesEmpty() {
        RouteRequest request = new RouteRequest("find leads", null, 1);
        assertThat(request.context().asMap()).isEmpty();
    }
}
