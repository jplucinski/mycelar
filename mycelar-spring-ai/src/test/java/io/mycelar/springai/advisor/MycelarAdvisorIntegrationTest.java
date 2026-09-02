package io.mycelar.springai.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mycelar.core.capability.CapabilityId;
import io.mycelar.core.routing.MycelarRuntime;
import io.mycelar.core.routing.SemanticMatcher;
import io.mycelar.springai.autoconfigure.MycelarSpringAiProperties;
import io.mycelar.springai.capability.CapabilityToolRegistry;
import io.mycelar.springai.context.MycelarContextRenderer;
import io.mycelar.springai.exception.MycelarCapabilityDeniedException;
import io.mycelar.springai.fixture.LeadGraphFixture;
import io.mycelar.springai.fixture.RecordingChatModel;
import io.mycelar.springai.fixture.StubTools;
import io.mycelar.springai.request.MycelarRequestMapper;
import io.mycelar.springai.request.MycelarSessionResolver;
import io.mycelar.springai.tool.MycelarCapabilityAuthorizer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

class MycelarAdvisorIntegrationTest {

    @Test
    void definitionOfDone() {
        AtomicInteger similarities = new AtomicInteger();
        SemanticMatcher lexical = new io.mycelar.core.routing.LexicalSemanticMatcher();
        SemanticMatcher counting =
                (intent, node) -> {
                    similarities.incrementAndGet();
                    return lexical.similarity(intent, node);
                };
        MycelarRuntime runtime =
                MycelarRuntime.builder()
                        .graph(LeadGraphFixture.graph())
                        .semanticMatcher(counting)
                        .build();

        AtomicBoolean updateCalled = new AtomicBoolean();
        AtomicBoolean sendCalled = new AtomicBoolean();
        ToolCallback read = StubTools.callback("readLead");
        ToolCallback update = StubTools.callback("updateLead", updateCalled);
        ToolCallback send = StubTools.callback("sendEmail", sendCalled);

        CapabilityToolRegistry registry =
                new CapabilityToolRegistry(
                        runtime, List.of(), List.of(read, update, send), List.of(), Map.of());
        MycelarSpringAiProperties properties = new MycelarSpringAiProperties();
        MycelarCapabilityAuthorizer authorizer = new MycelarCapabilityAuthorizer();
        MycelarAdvisor advisor =
                new MycelarAdvisor(
                        runtime,
                        new MycelarRequestMapper(),
                        new MycelarContextRenderer(true, true),
                        registry,
                        authorizer,
                        new MycelarSessionResolver(),
                        properties,
                        counting);

        RecordingChatModel model =
                new RecordingChatModel(
                        List.of(
                                new ChatResponse(
                                        List.of(
                                                new Generation(
                                                        AssistantMessage.builder()
                                                                .content("")
                                                                .toolCalls(
                                                                        List.of(
                                                                                new AssistantMessage.ToolCall(
                                                                                        "call-1",
                                                                                        "function",
                                                                                        "updateLead",
                                                                                        "{}")))
                                                                .build()))),
                                new ChatResponse(
                                        List.of(new Generation(new AssistantMessage("updated"))))));

        String content =
                ChatClient.builder(model)
                        .defaultAdvisors(advisor)
                        .build()
                        .prompt()
                        .user("Update this lead and email them")
                        .call()
                        .content();

        assertThat(content).isEqualTo("updated");
        assertThat(model.prompts).hasSize(2);
        ToolCallingChatOptions options = (ToolCallingChatOptions) model.prompts.get(0).getOptions();
        List<String> names =
                options.getToolCallbacks().stream()
                        .map(cb -> cb.getToolDefinition().name())
                        .toList();
        assertThat(names).containsExactlyInAnyOrder("readLead", "updateLead");
        assertThat(names).doesNotContain("sendEmail");
        assertThat(model.prompts.get(0).getSystemMessage().getText()).contains("Current entities are leads.");
        assertThat(updateCalled).isTrue();
        assertThat(sendCalled).isFalse();

        int afterChat = similarities.get();
        runtime.route(
                io.mycelar.core.routing.RouteRequest.builder()
                        .intent("Update this lead and email them")
                        .context(io.mycelar.core.routing.RoutingContext.empty())
                        .topK(1)
                        .build());
        int extra = similarities.get() - afterChat;
        assertThat(afterChat).isEqualTo(extra);

        ToolCallback guardedSend = authorizer.guard(send, new CapabilityId("send"));
        ToolContext snapshot = new ToolContext(options.getToolContext());
        assertThatThrownBy(() -> guardedSend.call("{}", snapshot))
                .isInstanceOf(MycelarCapabilityDeniedException.class);
        assertThat(sendCalled).isFalse();
    }
}
