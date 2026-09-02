package io.mycelar.core.context;

import io.mycelar.core.graph.CapsuleId;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record ContextCapsule(
        CapsuleId id,
        CapsuleType type,
        String text,
        int priority,
        double confidence,
        Optional<Duration> ttl) {

    public ContextCapsule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(ttl, "ttl");
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (Double.isNaN(confidence)
                || Double.isInfinite(confidence)
                || confidence < 0.0
                || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0,1]");
        }
    }
}
