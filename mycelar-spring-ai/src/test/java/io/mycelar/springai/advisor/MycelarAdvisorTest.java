package io.mycelar.springai.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mycelar.core.routing.MycelarRuntime;
import io.mycelar.core.routing.SemanticMatcher;
import io.mycelar.springai.autoconfigure.MycelarSpringAiProperties;
import io.mycelar.springai.capability.CapabilityToolRegistry;
import io.mycelar.springai.context.MycelarContextRenderer;
import io.mycelar.springai.exception.MycelarConfigurationException;
import io.mycelar.springai.fixture.LeadGraphFixture;
import io.mycelar.springai.fixture.RecordingChatModel;
import io.mycelar.springai.fixture.StubTools;
import io.mycelar.springai.request.MycelarRequestMapper;
import io.mycelar.springai.request.MycelarSessionResolver;
import io.mycelar.springai.tool.MycelarCapabilityAuthorizer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

class MycelarAdvisorTest {

    @Test
    void rejectsBadTopKAndOrder() {
        MycelarRuntime runtime = runtime();
        MycelarSpringAiProperties badTopK = new MycelarSpringAiProperties();
        badTopK.setTopK(0);
        assertThatThrownBy(() -> advisor(runtime, registry(runtime), badTopK))
                .isInstanceOf(MycelarConfigurationException.class);

        MycelarSpringAiProperties badOrder = new MycelarSpringAiProperties();
        badOrder.setAdvisorOrder(ToolCallingAdvisor.DEFAULT_ORDER);
        assertThatThrownBy(() -> advisor(runtime, registry(runtime), badOrder))
                .isInstanceOf(MycelarConfigurationException.class);
    }

    @Test
    void blankUserYieldsZeroToolsAndDoesNotRoute() {
        AtomicInteger similarities = new AtomicInteger();
        SemanticMatcher counting =
                (intent, node) -> {
                    similarities.incrementAndGet();
                    return 0.0;
                };
        MycelarRuntime runtime =
                MycelarRuntime.builder()
                        .graph(LeadGraphFixture.graph())
                        .semanticMatcher(counting)
                        .build();
        ChatClientRequest request =
                ChatClientRequest.builder()
                        .prompt(
                                new Prompt(
                                        List.of(
                                                new SystemMessage("app system"),
                                                new UserMessage("   "))))
                        .build();
        CapturingCallAdvisorChain chain = new CapturingCallAdvisorChain();
        advisor(runtime, registry(runtime), new MycelarSpringAiProperties()).adviseCall(request, chain);

        assertThat(similarities.get()).isZero();
        Prompt prompt = chain.request.prompt();
        ToolCallingChatOptions options = (ToolCallingChatOptions) prompt.getOptions();
        assertThat(options.getToolCallbacks()).isEmpty();
        assertThat(prompt.getSystemMessage().getText()).isEqualTo("app system");
        assertThat(prompt.getSystemMessage().getText()).doesNotContain("<mycelar-context>");
    }

    @Test
    void allowedToolsOnlyAndSystemBlockAppendedAndUnmanagedDropped() {
        MycelarRuntime runtime = runtime();
        RecordingChatModel model = RecordingChatModel.text("ok");
        ToolCallback unmanaged = StubTools.callback("unmanaged");

        ChatClient.builder(model)
                .defaultAdvisors(advisor(runtime, registry(runtime), new MycelarSpringAiProperties()))
                .build()
                .prompt()
                .system("app system")
                .user("Update this lead")
                .options(ToolCallingChatOptions.builder().toolCallbacks(unmanaged))
                .call()
                .content();

        PromptTools captured = PromptTools.from(model.prompts.get(0));
        assertThat(captured.names()).containsExactlyInAnyOrder("readLead", "updateLead");
        assertThat(captured.names()).doesNotContain("sendEmail", "unmanaged");
        assertThat(model.prompts.get(0).getSystemMessage().getText())
                .startsWith("app system")
                .contains("<mycelar-context>")
                .contains("Path:")
                .contains("Current entities are leads.");
    }

    private static MycelarRuntime runtime() {
        return MycelarRuntime.builder().graph(LeadGraphFixture.graph()).build();
    }

    private static CapabilityToolRegistry registry(MycelarRuntime runtime) {
        return new CapabilityToolRegistry(
                runtime,
                List.of(),
                List.of(
                        StubTools.callback("readLead"),
                        StubTools.callback("updateLead"),
                        StubTools.callback("sendEmail")),
                List.of(),
                Map.of());
    }

    private static MycelarAdvisor advisor(
            MycelarRuntime runtime, CapabilityToolRegistry registry, MycelarSpringAiProperties properties) {
        return new MycelarAdvisor(
                runtime,
                new MycelarRequestMapper(),
                new MycelarContextRenderer(
                        properties.isIncludeCapabilitiesInContext(),
                        properties.isIncludeBlockedInContext()),
                registry,
                new MycelarCapabilityAuthorizer(),
                new MycelarSessionResolver(),
                properties);
    }

    private static final class CapturingCallAdvisorChain implements CallAdvisorChain {
        private ChatClientRequest request;

        @Override
        public ChatClientResponse nextCall(ChatClientRequest request) {
            this.request = request;
            return ChatClientResponse.builder()
                    .chatResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))))
                    .build();
        }

        @Override
        public List<CallAdvisor> getCallAdvisors() {
            return List.of();
        }

        @Override
        public CallAdvisorChain copy(CallAdvisor advisor) {
            return this;
        }
    }

    private record PromptTools(List<String> names) {
        static PromptTools from(org.springframework.ai.chat.prompt.Prompt prompt) {
            ToolCallingChatOptions options = (ToolCallingChatOptions) prompt.getOptions();
            List<String> names =
                    options.getToolCallbacks() == null
                            ? List.of()
                            : options.getToolCallbacks().stream()
                                    .map(cb -> cb.getToolDefinition().name())
                                    .toList();
            return new PromptTools(names);
        }
    }
}
