package io.mycelar.core.routing;

import io.mycelar.core.graph.GraphNode;

public interface SemanticMatcher {
    double similarity(String intent, GraphNode node);

    default void clearQueryCache() {}
}
