package io.mycelar.springai.tool;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mycelar.core.capability.CapabilityId;
import io.mycelar.springai.exception.MycelarCapabilityDeniedException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

class MycelarToolContextTest {

    @Test
    void nullToolContextIsDenied() {
        assertThatThrownBy(() -> MycelarToolContext.from(null))
                .isInstanceOf(MycelarCapabilityDeniedException.class);
    }

    @Test
    void blankPathIsDenied() {
        assertThatThrownBy(() -> MycelarToolContext.from(context(" ", "session", Set.of())))
                .isInstanceOf(MycelarCapabilityDeniedException.class);
    }

    @Test
    void blankSessionIsDenied() {
        assertThatThrownBy(() -> MycelarToolContext.from(context("path", " ", Set.of())))
                .isInstanceOf(MycelarCapabilityDeniedException.class);
    }

    private static ToolContext context(String path, String sessionId, Set<CapabilityId> allowed) {
        return new ToolContext(
                Map.of(
                        MycelarToolContextKeys.PATH,
                        path,
                        MycelarToolContextKeys.SESSION_ID,
                        sessionId,
                        MycelarToolContextKeys.CAPABILITIES,
                        allowed));
    }
}
