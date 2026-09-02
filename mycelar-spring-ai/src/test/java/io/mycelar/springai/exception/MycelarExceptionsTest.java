package io.mycelar.springai.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.mycelar.springai.autoconfigure.MycelarSpringAiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;

class MycelarExceptionsTest {

    @Test
    void deniedIsRuntime() {
        assertThat(new MycelarCapabilityDeniedException("denied"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("denied");
    }

    @Test
    void propertiesDefaults() {
        MycelarSpringAiProperties p = new MycelarSpringAiProperties();
        assertThat(p.isEnabled()).isTrue();
        assertThat(p.getAdvisorOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 250);
        assertThat(p.getTopK()).isEqualTo(1);
        assertThat(p.isStrictToolBindings()).isFalse();
        assertThat(p.isIncludeCapabilitiesInContext()).isTrue();
        assertThat(p.isIncludeBlockedInContext()).isTrue();
        assertThat(p.getTools().getMappings()).isEmpty();
    }
}
