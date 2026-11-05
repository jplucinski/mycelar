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
import io.mycelar.core.context.CapsuleType;
import io.mycelar.core.context.ContextCapsule;
import io.mycelar.core.graph.CapsuleId;
import io.mycelar.core.graph.EdgeId;
import io.mycelar.core.graph.EdgeMetadata;
import io.mycelar.core.graph.EdgeType;
import io.mycelar.core.graph.GraphEdge;
import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.MycelarGraph;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.graph.NodeMetadata;
import io.mycelar.core.graph.NodeType;
import io.mycelar.core.routing.LexicalSemanticMatcher;
import io.mycelar.core.routing.MycelarRuntime;
import io.mycelar.core.routing.RoutingContext;
import io.mycelar.core.routing.SemanticMatcher;
import io.mycelar.springai.autoconfigure.MycelarSpringAiProperties;
import io.mycelar.springai.capability.CapabilityToolRegistry;
import io.mycelar.springai.context.MycelarContextRenderer;
import io.mycelar.springai.fixture.RecordingChatModel;
import io.mycelar.springai.request.MycelarRequestMapper;
import io.mycelar.springai.request.MycelarSessionResolver;
import io.mycelar.springai.tool.MycelarCapabilityAuthorizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class MycelarToolCallingAdvisorTest {

    @Test
    void reroutesAfterToolResponseAndKeepsBlockedCapabilityOut() {
        AtomicInteger clears = new AtomicInteger();
        List<String> intents = new ArrayList<>();
        SemanticMatcher lexical = new LexicalSemanticMatcher();
        SemanticMatcher spy =
                new SemanticMatcher() {
                    @Override
                    public double similarity(String intent, GraphNode node) {
                        intents.add(intent);
                        return lexical.similarity(intent, node);
                    }

                    @Override
                    public void clearQueryCache() {
                        clears.incrementAndGet();
                        lexical.clearQueryCache();
                    }
                };
        MycelarRuntime runtime =
                MycelarRuntime.builder()
                        .graph(graph())
                        .semanticMatcher(spy)
                        .constraintEvaluators(List.of(new NeedsOpenEvaluator()))
                        .build();
        MycelarSpringAiProperties properties = new MycelarSpringAiProperties();
        properties.setTopK(2);
        CapabilityToolRegistry registry =
                new CapabilityToolRegistry(
                        runtime,
                        List.of(),
                        List.of(
                                callback("readAlpha", "mutate gamma"),
                                callback("readBeta", "unused"),
                                callback("writeGamma", "unused"),
                                callback("writeDelta", "unused")),
                        List.of(),
                        Map.of());
        MycelarAdvisor advisor = advisor(runtime, registry, properties);
        MycelarToolCallingAdvisor toolAdvisor =
                MycelarToolCallingAdvisor.builder().mycelarAdvisor(advisor).build();

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
                                                                                        "readAlpha",
                                                                                        "{}")))
                                                                .build()))),
                                new ChatResponse(
                                        List.of(new Generation(new AssistantMessage("done"))))));

        String content =
                ChatClient.builder(model)
                        .defaultAdvisors(advisor, toolAdvisor)
                        .build()
                        .prompt()
                        .advisors(spec -> spec.param("mycelar.routing.open", Boolean.FALSE))
                        .user("inspect alpha")
                        .call()
                        .content();

        assertThat(content).isEqualTo("done");
        assertThat(model.prompts).hasSize(2);
        assertThat(clears).hasValue(2);
        assertThat(intents.stream().distinct().toList())
                .containsExactly("inspect alpha", "inspect alpha\n\nmutate gamma");

        List<String> first = names(model.prompts.get(0));
        assertThat(first).contains("readAlpha", "readBeta").doesNotContain("writeGamma", "writeDelta");

        List<String> second = names(model.prompts.get(1));
        assertThat(second).contains("readAlpha", "readBeta").doesNotContain("writeGamma", "writeDelta");
        String system = model.prompts.get(1).getSystemMessage().getText();
        assertThat(countOf(system, "<mycelar-context>")).isEqualTo(1);
        assertThat(system)
                .contains("Path: root / resource / read-group / read-alpha")
                .doesNotContain(" | ")
                .doesNotContain("write-group");
    }

    @Test
    void copiedBuilderStillReroutes() {
        Harness harness = harness();
        MycelarToolCallingAdvisor copied =
                MycelarToolCallingAdvisor.builder().mycelarAdvisor(harness.advisor).copy().build();

        ChatClientRequest routed = copied.doBeforeCall(toolResponseRequest(), null);

        assertThat(names(routed.prompt())).contains("readAlpha", "readBeta").doesNotContain("writeGamma", "writeDelta");
        String system = routed.prompt().getSystemMessage().getText();
        assertThat(countOf(system, "<mycelar-context>")).isEqualTo(1);
        assertThat(system).contains("Path: root / resource / read-group / read-alpha").doesNotContain(" | ");
    }

    @Test
    void beforeStreamReplacesContextBlock() {
        Harness harness = harness();
        MycelarToolCallingAdvisor toolAdvisor =
                MycelarToolCallingAdvisor.builder().mycelarAdvisor(harness.advisor).build();

        ChatClientRequest routed = toolAdvisor.doBeforeStream(toolResponseRequest(), null);

        assertThat(names(routed.prompt())).contains("readAlpha", "readBeta").doesNotContain("writeGamma", "writeDelta");
        String system = routed.prompt().getSystemMessage().getText();
        assertThat(countOf(system, "<mycelar-context>")).isEqualTo(1);
        assertThat(system).contains("Path: root / resource / read-group / read-alpha").doesNotContain(" | ");
    }

    @Test
    void builderRequiresMycelarAdvisor() {
        assertThatThrownBy(() -> MycelarToolCallingAdvisor.builder().build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void toolResponseWithoutUserMessageStillReroutes() {
        Harness harness = harness();
        MycelarToolCallingAdvisor toolAdvisor =
                MycelarToolCallingAdvisor.builder().mycelarAdvisor(harness.advisor).build();
        ChatClientRequest request =
                ChatClientRequest.builder()
                        .prompt(
                                new Prompt(
                                        List.of(
                                                new SystemMessage("app system"),
                                                ToolResponseMessage.builder()
                                                        .responses(
                                                                List.of(
                                                                        new ToolResponseMessage.ToolResponse(
                                                                                "call-1", "readAlpha", "mutate gamma")))
                                                        .build())))
                        .context(Map.of("mycelar.routing.open", Boolean.FALSE))
                        .build();

        ChatClientRequest routed = toolAdvisor.doBeforeCall(request, null);

        assertThat(names(routed.prompt())).contains("writeGamma").doesNotContain("writeDelta");
    }

    @Test
    void emptyToolResponsesDoNotAppendText() {
        Harness harness = harness();
        MycelarToolCallingAdvisor toolAdvisor =
                MycelarToolCallingAdvisor.builder().mycelarAdvisor(harness.advisor).build();
        ChatClientRequest request =
                ChatClientRequest.builder()
                        .prompt(
                                new Prompt(
                                        List.of(
                                                new SystemMessage("app system"),
                                                new UserMessage("inspect alpha"),
                                                ToolResponseMessage.builder().responses(List.of()).build())))
                        .context(Map.of("mycelar.routing.open", Boolean.FALSE))
                        .build();

        ChatClientRequest routed = toolAdvisor.doBeforeCall(request, null);

        assertThat(names(routed.prompt())).contains("readAlpha").doesNotContain("writeGamma");
    }

    @Test
    void nullToolResponseDataIsBlankIntent() {
        Harness harness = harness();
        MycelarToolCallingAdvisor toolAdvisor =
                MycelarToolCallingAdvisor.builder().mycelarAdvisor(harness.advisor).build();
        ChatClientRequest request =
                ChatClientRequest.builder()
                        .prompt(
                                new Prompt(
                                        List.of(
                                                ToolResponseMessage.builder()
                                                        .responses(
                                                                List.of(
                                                                        new ToolResponseMessage.ToolResponse(
                                                                                "call-1", "readAlpha", null)))
                                                        .build())))
                        .build();

        ChatClientRequest routed = toolAdvisor.doBeforeCall(request, null);

        assertThat(names(routed.prompt())).isEmpty();
        assertThat(routed.prompt().getSystemMessage().getText()).doesNotContain("<mycelar-context>");
    }

    private static ChatClientRequest toolResponseRequest() {
        return ChatClientRequest.builder()
                .prompt(
                        new Prompt(
                                List.of(
                                        new SystemMessage(
                                                "app system\n\n<mycelar-context>\nPath: old\n</mycelar-context>"),
                                        new UserMessage("inspect alpha"),
                                        ToolResponseMessage.builder()
                                                .responses(
                                                        List.of(
                                                                new ToolResponseMessage.ToolResponse(
                                                                        "call-1", "readAlpha", "mutate gamma")))
                                                .build())))
                .context(Map.of("mycelar.routing.open", Boolean.FALSE))
                .build();
    }

    private static Harness harness() {
        MycelarRuntime runtime =
                MycelarRuntime.builder()
                        .graph(graph())
                        .constraintEvaluators(List.of(new NeedsOpenEvaluator()))
                        .build();
        MycelarSpringAiProperties properties = new MycelarSpringAiProperties();
        properties.setTopK(2);
        CapabilityToolRegistry registry =
                new CapabilityToolRegistry(
                        runtime,
                        List.of(),
                        List.of(
                                callback("readAlpha", "mutate gamma"),
                                callback("readBeta", "unused"),
                                callback("writeGamma", "unused"),
                                callback("writeDelta", "unused")),
                        List.of(),
                        Map.of());
        return new Harness(advisor(runtime, registry, properties));
    }

    private record Harness(MycelarAdvisor advisor) {}

    private static List<String> names(Prompt prompt) {
        ToolCallingChatOptions options = (ToolCallingChatOptions) prompt.getOptions();
        if (options.getToolCallbacks() == null) {
            return List.of();
        }
        return options.getToolCallbacks().stream().map(cb -> cb.getToolDefinition().name()).toList();
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

    private static MycelarAdvisor advisor(
            MycelarRuntime runtime, CapabilityToolRegistry registry, MycelarSpringAiProperties properties) {
        return new MycelarAdvisor(
                runtime,
                new MycelarRequestMapper(),
                new MycelarContextRenderer(true, true),
                registry,
                new MycelarCapabilityAuthorizer(),
                new MycelarSessionResolver(),
                properties);
    }

    private static ToolCallback callback(String name, String result) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description(name)
                        .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                return result;
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return result;
            }
        };
    }

    private static MycelarGraph graph() {
        return MycelarGraph.builder()
                .node(node("root", NodeType.DOMAIN, "root", List.of(), List.of()))
                .node(node("resource", NodeType.RESOURCE, "resource", List.of(), List.of()))
                .node(
                        node(
                                "read-group",
                                NodeType.CONTEXT,
                                "read",
                                List.of("inspect"),
                                List.of(capsule("read-note", "Read group note."))))
                .node(
                        node(
                                "write-group",
                                NodeType.CONTEXT,
                                "write",
                                List.of("mutate"),
                                List.of(capsule("write-note", "Write group note."))))
                .node(capability("read-alpha", "read alpha", List.of("inspect", "alpha"), "readAlpha"))
                .node(capability("read-beta", "read beta", List.of("inspect", "beta"), "readBeta"))
                .node(capability("write-gamma", "write gamma", List.of("mutate", "gamma"), "writeGamma"))
                .node(capability("write-delta", "write delta", List.of("mutate", "delta"), "writeDelta"))
                .node(constraint("hold", "needs-open"))
                .edge(edge("root-resource", "root", "resource", EdgeType.CONTAINS))
                .edge(edge("resource-read", "resource", "read-group", EdgeType.CONTAINS))
                .edge(edge("resource-write", "resource", "write-group", EdgeType.CONTAINS))
                .edge(edge("read-alpha-edge", "read-group", "read-alpha", EdgeType.CONTAINS))
                .edge(edge("read-beta-edge", "read-group", "read-beta", EdgeType.CONTAINS))
                .edge(edge("write-gamma-edge", "write-group", "write-gamma", EdgeType.CONTAINS))
                .edge(edge("write-delta-edge", "write-group", "write-delta", EdgeType.CONTAINS))
                .edge(edge("read-write", "read-group", "write-group", EdgeType.RELATES_TO))
                .edge(edge("delta-hold", "write-delta", "hold", EdgeType.REQUIRES))
                .build();
    }

    private static GraphNode node(
            String id, NodeType type, String label, List<String> keywords, List<ContextCapsule> capsules) {
        return new GraphNode(
                new NodeId(id),
                type,
                new NodeMetadata(label, "", List.of(), keywords, Map.of()),
                capsules,
                null,
                null);
    }

    private static GraphNode capability(String id, String label, List<String> keywords, String operationId) {
        return new GraphNode(
                new NodeId(id),
                NodeType.CAPABILITY,
                new NodeMetadata(label, "", List.of(), keywords, Map.of()),
                List.of(),
                new CapabilityDescriptor(
                        new CapabilityId(id), id, "", new GatewayId("gateway"), operationId, Map.of()),
                null);
    }

    private static GraphNode constraint(String id, String type) {
        return new GraphNode(
                new NodeId(id),
                NodeType.CONSTRAINT,
                new NodeMetadata(id, "", List.of(), List.of(), Map.of()),
                List.of(),
                null,
                new Constraint(new ConstraintId(id), type, type, Map.of()));
    }

    private static ContextCapsule capsule(String id, String text) {
        return new ContextCapsule(new CapsuleId(id), CapsuleType.DOMAIN_CONTEXT, text, 1, 1.0, null);
    }

    private static GraphEdge edge(String edgeId, String source, String target, EdgeType type) {
        return new GraphEdge(
                new EdgeId(edgeId),
                new NodeId(source),
                new NodeId(target),
                type,
                1.0,
                new EdgeMetadata(Map.of()));
    }

    private static final class NeedsOpenEvaluator implements ConstraintEvaluator {
        @Override
        public boolean supports(Constraint constraint) {
            return "needs-open".equals(constraint.type());
        }

        @Override
        public ConstraintDecision evaluate(Constraint constraint, RoutingContext context) {
            return context.get("open").filter(Boolean.TRUE::equals).isPresent()
                    ? ConstraintDecision.ALLOW
                    : ConstraintDecision.BLOCK;
        }
    }
}
