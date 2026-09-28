package io.mycelar.core.graph;

import io.mycelar.core.capability.CapabilityDescriptor;
import io.mycelar.core.constraint.Constraint;
import io.mycelar.core.context.ContextCapsule;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record GraphNode(
        NodeId id,
        NodeType type,
        NodeMetadata metadata,
        List<ContextCapsule> capsules,
        CapabilityDescriptor capabilityDescriptor,
        Constraint constraintDescriptor) {

    public GraphNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(capsules, "capsules");
        capsules = List.copyOf(capsules);
    }

    public Optional<CapabilityDescriptor> capability() {
        return Optional.ofNullable(capabilityDescriptor);
    }

    public Optional<Constraint> constraint() {
        return Optional.ofNullable(constraintDescriptor);
    }
}
