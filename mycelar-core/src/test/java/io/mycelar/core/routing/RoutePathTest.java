package io.mycelar.core.routing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mycelar.core.graph.EdgeId;
import io.mycelar.core.graph.NodeId;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutePathTest {

    @Test
    void emptyNodeIdsAreIae() {
        assertThatThrownBy(() -> new RoutePath(List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void edgeCountMustMatchHops() {
        assertThatThrownBy(() -> new RoutePath(List.of(new NodeId("a")), List.of(new EdgeId("e"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
