package io.mycelar.springai.capability;

import java.util.List;
import java.util.Objects;

public record CapabilityToolBindings(List<CapabilityToolBinding> bindings) {
    public CapabilityToolBindings {
        Objects.requireNonNull(bindings, "bindings");
        bindings = List.copyOf(bindings);
    }
}
