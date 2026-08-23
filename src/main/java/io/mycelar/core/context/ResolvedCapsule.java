package io.mycelar.core.context;

import io.mycelar.core.graph.NodeId;
import java.util.Objects;

public record ResolvedCapsule(ContextCapsule capsule, NodeId sourceNodeId, int pathIndex) {

    public ResolvedCapsule {
        Objects.requireNonNull(capsule, "capsule");
        Objects.requireNonNull(sourceNodeId, "sourceNodeId");
    }
}
