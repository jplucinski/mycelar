package io.mycelar.springai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mycelar.core.capability.CapabilityId;
import io.mycelar.springai.exception.MycelarCapabilityDeniedException;
import io.mycelar.springai.fixture.StubTools;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

class MycelarGuardedToolCallbackTest {

    private final MycelarCapabilityAuthorizer authorizer = new MycelarCapabilityAuthorizer();
    private final CapabilityId read = new CapabilityId("read");

    @Test
    void allowedInvokesDelegate() {
        AtomicBoolean called = new AtomicBoolean();
        ToolCallback delegate = StubTools.callback("readLead", called);
        ToolCallback guarded = new MycelarGuardedToolCallback(delegate, read, authorizer);
        ToolContext ctx =
                new ToolContext(authorizer.snapshot("leads / read", "s1", Set.of(read)));

        assertThat(guarded.call("{}", ctx)).isEqualTo("readLead-ok");
        assertThat(called).isTrue();
        assertThat(guarded.getToolDefinition().name()).isEqualTo("readLead");
    }

    @Test
    void deniedDoesNotInvokeDelegate() {
        AtomicBoolean called = new AtomicBoolean();
        ToolCallback delegate = StubTools.callback("sendEmail", called);
        ToolCallback guarded =
                new MycelarGuardedToolCallback(delegate, new CapabilityId("send"), authorizer);
        ToolContext ctx = new ToolContext(authorizer.snapshot("leads", "s1", Set.of(read)));

        assertThatThrownBy(() -> guarded.call("{}", ctx))
                .isInstanceOf(MycelarCapabilityDeniedException.class);
        assertThat(called).isFalse();
    }

    @Test
    void missingContextDoesNotInvokeDelegate() {
        AtomicBoolean called = new AtomicBoolean();
        ToolCallback guarded =
                new MycelarGuardedToolCallback(StubTools.callback("readLead", called), read, authorizer);

        assertThatThrownBy(() -> guarded.call("{}")).isInstanceOf(MycelarCapabilityDeniedException.class);
        assertThatThrownBy(() -> guarded.call("{}", null))
                .isInstanceOf(MycelarCapabilityDeniedException.class);
        assertThatThrownBy(() -> guarded.call("{}", new ToolContext(Map.of())))
                .isInstanceOf(MycelarCapabilityDeniedException.class);
        assertThat(called).isFalse();
    }

    @Test
    void stringCapabilityCollectionIsDenied() {
        AtomicBoolean called = new AtomicBoolean();
        ToolCallback guarded =
                new MycelarGuardedToolCallback(StubTools.callback("readLead", called), read, authorizer);
        ToolContext ctx =
                new ToolContext(
                        Map.of(
                                MycelarToolContextKeys.PATH,
                                "leads",
                                MycelarToolContextKeys.SESSION_ID,
                                "s1",
                                MycelarToolContextKeys.CAPABILITIES,
                                java.util.List.of("read")));
        assertThatThrownBy(() -> guarded.call("{}", ctx))
                .isInstanceOf(MycelarCapabilityDeniedException.class);
        assertThat(called).isFalse();
    }

    @Test
    void listOfCapabilityIdIsAccepted() {
        AtomicBoolean called = new AtomicBoolean();
        ToolCallback guarded =
                new MycelarGuardedToolCallback(StubTools.callback("readLead", called), read, authorizer);
        ToolContext ctx =
                new ToolContext(
                        Map.of(
                                MycelarToolContextKeys.PATH,
                                "leads",
                                MycelarToolContextKeys.SESSION_ID,
                                "s1",
                                MycelarToolContextKeys.CAPABILITIES,
                                java.util.List.of(read)));
        assertThat(guarded.call("{}", ctx)).isEqualTo("readLead-ok");
        assertThat(called).isTrue();
    }
}
