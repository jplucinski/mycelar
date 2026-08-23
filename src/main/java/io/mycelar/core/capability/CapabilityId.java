package io.mycelar.core.capability;

import java.util.Objects;

public record CapabilityId(String value) {

    public CapabilityId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("CapabilityId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
