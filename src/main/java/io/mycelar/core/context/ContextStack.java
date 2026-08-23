package io.mycelar.core.context;

import java.util.List;
import java.util.Objects;

public record ContextStack(List<ResolvedCapsule> capsules) {

    public ContextStack {
        Objects.requireNonNull(capsules, "capsules");
        capsules = List.copyOf(capsules);
    }
}
