package io.mycelar.core.capability;

import io.mycelar.core.constraint.ConstraintDecision;
import io.mycelar.core.constraint.ConstraintId;
import java.util.List;
import java.util.Objects;

public record BlockedCapability(
        CapabilityId capabilityId, ConstraintDecision decision, List<ConstraintId> constraintIds) {

    public BlockedCapability {
        Objects.requireNonNull(capabilityId, "capabilityId");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(constraintIds, "constraintIds");
        constraintIds = List.copyOf(constraintIds);
    }
}
