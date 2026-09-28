package io.mycelar.core.validation;

import java.util.List;
import java.util.stream.Collectors;

public final class GraphValidationException extends RuntimeException {

    private final List<Violation> violations;

    public GraphValidationException(List<Violation> violations) {
        super(
                violations.stream()
                        .map(v -> v.path() + ": " + v.message())
                        .collect(Collectors.joining("; ")));
        this.violations = List.copyOf(violations);
    }

    public List<Violation> violations() {
        return violations;
    }
}
