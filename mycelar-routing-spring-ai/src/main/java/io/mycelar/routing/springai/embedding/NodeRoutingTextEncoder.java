package io.mycelar.routing.springai.embedding;

import io.mycelar.core.graph.GraphNode;

public interface NodeRoutingTextEncoder {
    String encode(GraphNode node);

    static NodeRoutingTextEncoder canonical() {
        return new DefaultNodeRoutingTextEncoder();
    }
}
