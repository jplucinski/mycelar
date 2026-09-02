package io.mycelar.core.routing;

import io.mycelar.core.graph.EdgeId;
import io.mycelar.core.graph.NodeId;
import java.util.List;
import java.util.Objects;

public record RoutePath(List<NodeId> nodeIds, List<EdgeId> edgeIds) {

    public RoutePath {
        Objects.requireNonNull(nodeIds, "nodeIds");
        Objects.requireNonNull(edgeIds, "edgeIds");
        if (nodeIds.isEmpty()) {
            throw new IllegalArgumentException("nodeIds must be non-empty");
        }
        if (edgeIds.size() != nodeIds.size() - 1) {
            throw new IllegalArgumentException("edgeIds.size() must equal nodeIds.size() - 1");
        }
        nodeIds = List.copyOf(nodeIds);
        edgeIds = List.copyOf(edgeIds);
    }
}
