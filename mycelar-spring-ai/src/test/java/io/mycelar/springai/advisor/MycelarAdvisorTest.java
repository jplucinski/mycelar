package io.mycelar.springai.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mycelar.core.capability.CapabilityDescriptor;
import io.mycelar.core.capability.CapabilityId;
import io.mycelar.core.capability.GatewayId;
import io.mycelar.core.constraint.Constraint;
import io.mycelar.core.constraint.ConstraintDecision;
import io.mycelar.core.constraint.ConstraintEvaluator;
import io.mycelar.core.constraint.ConstraintId;
import io.mycelar.core.graph.EdgeId;
import io.mycelar.core.graph.EdgeMetadata;
import io.mycelar.core.graph.EdgeType;
import io.mycelar.core.graph.GraphEdge;
import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.MycelarGraph;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.graph.NodeMetadata;
import io.mycelar.core.graph.NodeType;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

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
    void clearsQueryCacheAfterRoute() {
        TrackingMatcher tracking = new TrackingMatcher();
        MycelarRuntime runtime =
                MycelarRuntime.builder()
                        .graph(LeadGraphFixture.graph())
                        .semanticMatcher(tracking)
                        .build();
        ChatClientRequest request =
                ChatClientRequest.builder()
                        .prompt(new Prompt(List.of(new UserMessage("Update this lead"))))
                        .build();
        CapturingCallAdvisorChain chain = new CapturingCallAdvisorChain();
        advisor(runtime, registry(runtime), new MycelarSpringAiProperties()).adviseCall(request, chain);
        assertThat(tracking.clears.get()).isEqualTo(1);
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

    @Test
    void topKUsesPrimaryPathWhenThatCapabilityIsBlocked() {
        MycelarRuntime runtime =
                MycelarRuntime.builder()
                        .graph(twoGroupGraph(true))
                        .semanticMatcher(prefer("cap-no"))
                        .constraintEvaluators(List.of(new AlwaysBlockEvaluator()))
                        .build();
        MycelarSpringAiProperties properties = new MycelarSpringAiProperties();
        properties.setTopK(2);
        CapturingCallAdvisorChain chain = new CapturingCallAdvisorChain();
        advisor(runtime, twoToolRegistry(runtime), properties)
                .adviseCall(userRequest("use both"), chain);

        PromptTools captured = PromptTools.from(chain.request.prompt());
        assertThat(captured.names()).isEmpty();
        assertThat(capabilities(chain.request.prompt())).isEmpty();
        String system = chain.request.prompt().getSystemMessage().getText();
        assertThat(system).contains("Path: root / group-b / cap-no");
        assertThat(system).contains("cap-no: BLOCK");
        assertThat(system).doesNotContain(" | ").doesNotContain("cap-ok").doesNotContain("okTool");
        assertThat(countOf(system, "<mycelar-context>")).isEqualTo(1);
    }

    @Test
    void topKUsesPrimaryConeOnly() {
        MycelarRuntime runtime =
                MycelarRuntime.builder()
                        .graph(twoGroupGraph(false))
                        .semanticMatcher(leafMatcher())
                        .build();
        MycelarSpringAiProperties properties = new MycelarSpringAiProperties();
        properties.setTopK(2);
        CapturingCallAdvisorChain chain = new CapturingCallAdvisorChain();
        advisor(runtime, twoToolRegistry(runtime), properties)
                .adviseCall(userRequest("use both"), chain);

        PromptTools captured = PromptTools.from(chain.request.prompt());
        assertThat(captured.names()).containsExactly("okTool");
        assertThat(capabilities(chain.request.prompt())).containsExactly(new CapabilityId("cap-ok"));
        String system = chain.request.prompt().getSystemMessage().getText();
        assertThat(system).contains("Path: root / group-a / cap-ok");
        assertThat(system).doesNotContain(" | ").doesNotContain("cap-no").doesNotContain("noTool");
        assertThat(countOf(system, "<mycelar-context>")).isEqualTo(1);
    }

    @Test
    void adviseStreamRoutesLikeCall() {
        CapturingStreamAdvisorChain stream = new CapturingStreamAdvisorChain();
        advisor(runtime(), registry(runtime()), new MycelarSpringAiProperties())
                .adviseStream(userRequest("Update this lead"), stream)
                .blockLast();

        PromptTools captured = PromptTools.from(stream.request.prompt());
        assertThat(captured.names()).containsExactlyInAnyOrder("readLead", "updateLead");
        assertThat(stream.request.prompt().getSystemMessage().getText()).contains("<mycelar-context>");
    }

    @Test
    void emptyRouteYieldsZeroTools() {
        MycelarRuntime runtime = MycelarRuntime.builder().graph(MycelarGraph.builder().build()).build();
        CapturingCallAdvisorChain chain = new CapturingCallAdvisorChain();
        advisor(runtime, registry(runtime), new MycelarSpringAiProperties())
                .adviseCall(userRequest("hello"), chain);

        Prompt prompt = chain.request.prompt();
        assertThat(PromptTools.from(prompt).names()).isEmpty();
        assertThat(prompt.getSystemMessage().getText()).isEqualTo("app system");
        assertThat(prompt.getSystemMessage().getText()).doesNotContain("<mycelar-context>");
    }

    @Test
    void nullIntentSkipsRoute() {
        AtomicInteger similarities = new AtomicInteger();
        SemanticMatcher counting =
                (intent, node) -> {
                    similarities.incrementAndGet();
                    return 0.0;
                };
        MycelarRuntime runtime =
                MycelarRuntime.builder().graph(LeadGraphFixture.graph()).semanticMatcher(counting).build();
        advisor(runtime, registry(runtime), new MycelarSpringAiProperties())
                .route(userRequest("Update this lead"), null);

        assertThat(similarities.get()).isZero();
    }

    @Test
    void replacesPreviousContextBlock() {
        ChatClientRequest request =
                ChatClientRequest.builder()
                        .prompt(
                                new Prompt(
                                        List.of(
                                                new SystemMessage(
                                                        "app system\n\n<mycelar-context>\nPath: old\n</mycelar-context>"),
                                                new UserMessage("Update this lead"))))
                        .build();
        CapturingCallAdvisorChain chain = new CapturingCallAdvisorChain();
        advisor(runtime(), registry(runtime()), new MycelarSpringAiProperties()).adviseCall(request, chain);

        String system = chain.request.prompt().getSystemMessage().getText();
        assertThat(countOf(system, "<mycelar-context>")).isEqualTo(1);
        assertThat(system).startsWith("app system").doesNotContain("Path: old");
    }

    @Test
    void dropsUnmanagedCallbackAndKeepsNamedOne() {
        ToolCallback readLead = StubTools.callback("readLead");
        ToolCallback unmanaged = StubTools.callback("unmanaged");
        ChatClientRequest request =
                ChatClientRequest.builder()
                        .prompt(
                                new Prompt(
                                        List.of(new SystemMessage("app system"), new UserMessage("Update this lead")),
                                        ToolCallingChatOptions.builder()
                                                .toolCallbacks(List.of(readLead, unmanaged))
                                                .build()))
                        .build();
        CapturingCallAdvisorChain chain = new CapturingCallAdvisorChain();
        advisor(runtime(), registry(runtime()), new MycelarSpringAiProperties()).adviseCall(request, chain);

        assertThat(PromptTools.from(chain.request.prompt()).names())
                .contains("readLead", "updateLead")
                .doesNotContain("unmanaged");
    }

    @Test
    void mergesExistingToolContext() {
        ChatClientRequest request =
                ChatClientRequest.builder()
                        .prompt(
                                new Prompt(
                                        List.of(new UserMessage("Update this lead")),
                                        ToolCallingChatOptions.builder()
                                                .toolContext(Map.of("prior", "kept"))
                                                .build()))
                        .build();
        CapturingCallAdvisorChain chain = new CapturingCallAdvisorChain();
        advisor(runtime(), registry(runtime()), new MycelarSpringAiProperties()).adviseCall(request, chain);

        assertThat(chain.request.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
        ToolCallingChatOptions options = (ToolCallingChatOptions) chain.request.prompt().getOptions();
        assertThat(options.getToolContext()).containsEntry("prior", "kept");
        assertThat(options.getToolContext()).containsKey("mycelar.capabilities");
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
            MycelarRuntime runtime,
            CapabilityToolRegistry registry,
            MycelarSpringAiProperties properties) {
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

    private static final class CapturingStreamAdvisorChain implements StreamAdvisorChain {
        private ChatClientRequest request;

        @Override
        public Flux<ChatClientResponse> nextStream(ChatClientRequest request) {
            this.request = request;
            return Flux.just(
                    ChatClientResponse.builder()
                            .chatResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))))
                            .build());
        }

        @Override
        public List<StreamAdvisor> getStreamAdvisors() {
            return List.of();
        }

        @Override
        public StreamAdvisorChain copy(StreamAdvisor advisor) {
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

    private static final class TrackingMatcher implements SemanticMatcher {
        private final AtomicInteger clears = new AtomicInteger();

        @Override
        public double similarity(String intent, GraphNode node) {
            return 0.5;
        }

        @Override
        public void clearQueryCache() {
            clears.incrementAndGet();
        }
    }

    private static ChatClientRequest userRequest(String text) {
        return ChatClientRequest.builder()
                .prompt(new Prompt(List.of(new SystemMessage("app system"), new UserMessage(text))))
                .build();
    }

    private static CapabilityToolRegistry twoToolRegistry(MycelarRuntime runtime) {
        return new CapabilityToolRegistry(
                runtime,
                List.of(),
                List.of(StubTools.callback("okTool"), StubTools.callback("noTool")),
                List.of(),
                Map.of());
    }

    private static SemanticMatcher leafMatcher() {
        return prefer("cap-ok");
    }

    private static SemanticMatcher prefer(String winner) {
        return (intent, node) -> {
            String id = node.id().value();
            if (winner.equals(id)) {
                return 1.0;
            }
            if ("cap-ok".equals(id) || "cap-no".equals(id)) {
                return 0.5;
            }
            return 0.0;
        };
    }

    @SuppressWarnings("unchecked")
    private static Set<CapabilityId> capabilities(org.springframework.ai.chat.prompt.Prompt prompt) {
        ToolCallingChatOptions options = (ToolCallingChatOptions) prompt.getOptions();
        return (Set<CapabilityId>) options.getToolContext().get("mycelar.capabilities");
    }

    private static int countOf(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + needle.length();
        }
    }

    private static MycelarGraph twoGroupGraph(boolean blockSecond) {
        MycelarGraph.Builder builder =
                MycelarGraph.builder()
                        .node(plain("root", NodeType.DOMAIN))
                        .node(plain("group-a", NodeType.CONTEXT))
                        .node(plain("group-b", NodeType.CONTEXT))
                        .node(capability("cap-ok", "okTool"))
                        .node(capability("cap-no", "noTool"))
                        .edge(contains("root-a", "root", "group-a"))
                        .edge(contains("root-b", "root", "group-b"))
                        .edge(contains("a-ok", "group-a", "cap-ok"))
                        .edge(contains("b-no", "group-b", "cap-no"));
        if (blockSecond) {
            builder.node(constraint("hold"))
                    .edge(requires("no-hold", "cap-no", "hold"));
        }
        return builder.build();
    }

    private static GraphNode plain(String id, NodeType type) {
        return new GraphNode(
                new NodeId(id),
                type,
                new NodeMetadata(id, "", List.of(), List.of(), Map.of()),
                List.of(),
                null,
                null);
    }

    private static GraphNode capability(String id, String operationId) {
        return new GraphNode(
                new NodeId(id),
                NodeType.CAPABILITY,
                new NodeMetadata(id, "", List.of(), List.of(), Map.of()),
                List.of(),
                new CapabilityDescriptor(
                        new CapabilityId(id), id, "", new GatewayId("gateway"), operationId, Map.of()),
                null);
    }

    private static GraphNode constraint(String id) {
        return new GraphNode(
                new NodeId(id),
                NodeType.CONSTRAINT,
                new NodeMetadata(id, "", List.of(), List.of(), Map.of()),
                List.of(),
                null,
                new Constraint(new ConstraintId(id), "always-block", "block", Map.of()));
    }

    private static GraphEdge contains(String edgeId, String source, String target) {
        return new GraphEdge(
                new EdgeId(edgeId),
                new NodeId(source),
                new NodeId(target),
                EdgeType.CONTAINS,
                1.0,
                new EdgeMetadata(Map.of()));
    }

    private static GraphEdge requires(String edgeId, String source, String target) {
        return new GraphEdge(
                new EdgeId(edgeId),
                new NodeId(source),
                new NodeId(target),
                EdgeType.REQUIRES,
                1.0,
                new EdgeMetadata(Map.of()));
    }

    private static final class AlwaysBlockEvaluator implements ConstraintEvaluator {
        @Override
        public boolean supports(Constraint constraint) {
            return "always-block".equals(constraint.type());
        }

        @Override
        public ConstraintDecision evaluate(Constraint constraint, io.mycelar.core.routing.RoutingContext context) {
            return ConstraintDecision.BLOCK;
        }
    }
}
