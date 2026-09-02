package io.mycelar.core.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mycelar.core.capability.CapabilityScope;
import io.mycelar.core.context.ContextStack;
import io.mycelar.core.graph.NodeId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RouteResultTest {

    @Test
    void primaryEmptyWhenNoCandidates() {
        assertThat(new RouteResult(List.of()).primary()).isEmpty();
    }

    @Test
    void nullCandidatesThrow() {
        assertThatThrownBy(() -> new RouteResult(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void primaryIsFirstCandidate() {
        RouteCandidate first = candidate("a");
        RouteCandidate second = candidate("b");
        assertThat(new RouteResult(List.of(first, second)).primary()).contains(first);
    }

    private static RouteCandidate candidate(String node) {
        return new RouteCandidate(
                new RoutePath(List.of(new NodeId(node)), List.of()),
                new RouteScore(1.0, Map.of()),
                new ContextStack(List.of()),
                new CapabilityScope(Set.of(), Set.of()),
                List.of());
    }
}
