package io.mycelar.core.capability;

import java.util.Objects;
import java.util.Set;

public record CapabilityScope(Set<CapabilityId> allowed, Set<BlockedCapability> blocked) {

    public CapabilityScope {
        Objects.requireNonNull(allowed, "allowed");
        Objects.requireNonNull(blocked, "blocked");
        allowed = Set.copyOf(allowed);
        blocked = Set.copyOf(blocked);
    }
}
