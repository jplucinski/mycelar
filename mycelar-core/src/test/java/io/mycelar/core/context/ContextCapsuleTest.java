package io.mycelar.core.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mycelar.core.graph.CapsuleId;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ContextCapsuleTest {

    @Test
    void blankTextIsIae() {
        assertThatThrownBy(() -> capsule("  ", 1.0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confidenceOutsideUnitIntervalIsIae() {
        assertThatThrownBy(() -> capsule("text", -0.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> capsule("text", 1.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> capsule("text", Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ttlIsOptional() {
        assertThat(capsule("text", 1.0).ttl()).isEmpty();
        ContextCapsule withTtl =
                new ContextCapsule(
                        new CapsuleId("c"), CapsuleType.INSTRUCTION, "text", 1, 1.0, Duration.ofMinutes(5));
        assertThat(withTtl.ttl()).contains(Duration.ofMinutes(5));
    }

    private static ContextCapsule capsule(String text, double confidence) {
        return new ContextCapsule(new CapsuleId("c"), CapsuleType.INSTRUCTION, text, 1, confidence, null);
    }
}
