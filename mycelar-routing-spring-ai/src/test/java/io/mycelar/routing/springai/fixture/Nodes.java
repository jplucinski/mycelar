package io.mycelar.routing.springai.fixture;

import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.graph.NodeMetadata;
import io.mycelar.core.graph.NodeType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Nodes {

    private Nodes() {}

    public static GraphNode node(
            String id, String label, String description, List<String> aliases, List<String> keywords) {
        return new GraphNode(
                new NodeId(id),
                NodeType.RESOURCE,
                new NodeMetadata(label, description, aliases, keywords, Map.of()),
                List.of(),
                null,
                null);
    }
}
