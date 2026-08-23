package io.mycelar.core.capability;

import java.util.Map;
import java.util.Objects;

public record CapabilityDescriptor(
        CapabilityId id,
        String name,
        String description,
        GatewayId gatewayId,
        String operationId,
        Map<String, Object> metadata) {

    public CapabilityDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(gatewayId, "gatewayId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(metadata, "metadata");
        if (operationId.isBlank()) {
            throw new IllegalArgumentException("operationId must not be blank");
        }
        metadata = Map.copyOf(metadata);
    }
}
