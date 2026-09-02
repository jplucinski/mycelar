package io.mycelar.core.capability;

import java.util.Objects;

public record GatewayId(String value) {

    public GatewayId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("GatewayId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
