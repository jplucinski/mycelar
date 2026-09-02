package io.mycelar.springai.advisor;

import io.mycelar.core.capability.CapabilityId;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.routing.MycelarRuntime;
import io.mycelar.core.routing.RouteCandidate;
import io.mycelar.core.routing.RouteRequest;
import io.mycelar.springai.autoconfigure.MycelarSpringAiProperties;
import io.mycelar.springai.capability.CapabilityToolBinding;
import io.mycelar.springai.capability.CapabilityToolRegistry;
import io.mycelar.springai.context.MycelarContextRenderer;
import io.mycelar.springai.exception.MycelarConfigurationException;
import io.mycelar.springai.request.MycelarRequestMapper;
import io.mycelar.springai.request.MycelarSessionResolver;
import io.mycelar.springai.tool.MycelarCapabilityAuthorizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

public final class MycelarAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String ROUTE_CONTEXT_KEY = "mycelar.route";

    private static final Logger log = LoggerFactory.getLogger(MycelarAdvisor.class);

    private final MycelarRuntime runtime;
    private final MycelarRequestMapper requestMapper;
    private final MycelarContextRenderer renderer;
    private final CapabilityToolRegistry registry;
    private final MycelarCapabilityAuthorizer authorizer;
    private final MycelarSessionResolver sessionResolver;
    private final MycelarSpringAiProperties properties;

    public MycelarAdvisor(
            MycelarRuntime runtime,
            MycelarRequestMapper requestMapper,
            MycelarContextRenderer renderer,
            CapabilityToolRegistry registry,
            MycelarCapabilityAuthorizer authorizer,
            MycelarSessionResolver sessionResolver,
            MycelarSpringAiProperties properties) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.requestMapper = Objects.requireNonNull(requestMapper, "requestMapper");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.sessionResolver = Objects.requireNonNull(sessionResolver, "sessionResolver");
        this.properties = Objects.requireNonNull(properties, "properties");
        if (properties.getTopK() < 1) {
            throw new MycelarConfigurationException("mycelar.spring-ai.top-k must be >= 1");
        }
        if (properties.getAdvisorOrder() >= ToolCallingAdvisor.DEFAULT_ORDER) {
            throw new MycelarConfigurationException(
                    "mycelar.spring-ai.advisor-order must be < ToolCallingAdvisor.DEFAULT_ORDER");
        }
    }

    @Override
    public String getName() {
        return "Mycelar";
    }

    @Override
    public int getOrder() {
        return properties.getAdvisorOrder();
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(apply(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(apply(request));
    }

    ChatClientRequest apply(ChatClientRequest request) {
        Objects.requireNonNull(request, "request");
        String sessionId = sessionResolver.resolve(request);
        String intent = request.prompt().getUserMessage().getText();
        ChatClientRequest.Builder builder = request.mutate().context(MycelarSessionResolver.SESSION_ID_KEY, sessionId);

        if (intent == null || intent.isBlank()) {
            return builder.prompt(withTools(request.prompt(), List.of(), sessionId, "", Set.of(), List.of()))
                    .build();
        }

        RouteRequest routeRequest = requestMapper.map(request, properties.getTopK());
        var primary = runtime.route(routeRequest).primary();
        if (primary.isEmpty()) {
            return builder.prompt(withTools(request.prompt(), List.of(), sessionId, "", Set.of(), List.of()))
                    .build();
        }

        RouteCandidate candidate = primary.get();
        String path =
                candidate.path().nodeIds().stream().map(NodeId::value).collect(Collectors.joining(" / "));
        Set<CapabilityId> allowed = candidate.capabilityScope().allowed();
        var bindings = registry.resolve(allowed, properties.isStrictToolBindings());
        List<ToolCallback> guarded = new ArrayList<>();
        for (CapabilityToolBinding binding : bindings) {
            guarded.add(authorizer.guard(binding.toolCallback(), binding.capabilityId()));
        }

        Prompt prompt = request.prompt();
        String block = renderer.render(candidate);
        Prompt withSystem =
                prompt.augmentSystemMessage(
                        systemMessage -> {
                            String existing = systemMessage.getText();
                            String next =
                                    existing == null || existing.isBlank() ? block : existing + "\n\n" + block;
                            return systemMessage.mutate().text(next).build();
                        });
        Prompt replaced = withTools(withSystem, guarded, sessionId, path, allowed, existingCallbacks(prompt));
        return builder.prompt(replaced).context(ROUTE_CONTEXT_KEY, candidate).build();
    }

    private Prompt withTools(
            Prompt prompt,
            List<ToolCallback> replacement,
            String sessionId,
            String path,
            Set<CapabilityId> allowed,
            List<ToolCallback> previous) {
        dropUnmanaged(previous, replacement);
        Map<String, Object> merged = new HashMap<>();
        ChatOptions options = prompt.getOptions();
        if (options instanceof ToolCallingChatOptions toolOptions && toolOptions.getToolContext() != null) {
            merged.putAll(toolOptions.getToolContext());
        }
        merged.putAll(authorizer.snapshot(path, sessionId, allowed));

        ToolCallingChatOptions.Builder optionsBuilder = ToolCallingChatOptions.builder();
        if (options != null) {
            optionsBuilder
                    .model(options.getModel())
                    .frequencyPenalty(options.getFrequencyPenalty())
                    .maxTokens(options.getMaxTokens())
                    .presencePenalty(options.getPresencePenalty())
                    .stopSequences(options.getStopSequences())
                    .temperature(options.getTemperature())
                    .topK(options.getTopK())
                    .topP(options.getTopP());
        }
        ToolCallingChatOptions newOptions =
                optionsBuilder.toolCallbacks(replacement).toolContext(merged).build();
        return prompt.mutate().chatOptions(newOptions).build();
    }

    private static List<ToolCallback> existingCallbacks(Prompt prompt) {
        ChatOptions options = prompt.getOptions();
        if (options instanceof ToolCallingChatOptions toolOptions && toolOptions.getToolCallbacks() != null) {
            return toolOptions.getToolCallbacks();
        }
        return List.of();
    }

    private static void dropUnmanaged(List<ToolCallback> previous, List<ToolCallback> replacement) {
        Set<String> kept = new HashSet<>();
        for (ToolCallback callback : replacement) {
            kept.add(callback.getToolDefinition().name());
        }
        List<String> dropped = new ArrayList<>();
        for (ToolCallback callback : previous) {
            String name = callback.getToolDefinition().name();
            if (!kept.contains(name)) {
                dropped.add(name);
            }
        }
        if (!dropped.isEmpty()) {
            log.warn("Dropping unmanaged tools: {}", dropped);
        }
    }
}
