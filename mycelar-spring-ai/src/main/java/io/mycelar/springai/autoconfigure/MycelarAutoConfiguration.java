package io.mycelar.springai.autoconfigure;

import io.mycelar.core.constraint.ConstraintEvaluator;
import io.mycelar.core.graph.MycelarGraph;
import io.mycelar.core.routing.MycelarRuntime;
import io.mycelar.core.routing.RouteScorer;
import io.mycelar.core.routing.RoutingLimits;
import io.mycelar.core.routing.SemanticMatcher;
import io.mycelar.springai.advisor.MycelarAdvisor;
import io.mycelar.springai.capability.CapabilityToolBindings;
import io.mycelar.springai.capability.CapabilityToolRegistry;
import io.mycelar.springai.context.MycelarContextRenderer;
import io.mycelar.springai.request.MycelarRequestMapper;
import io.mycelar.springai.request.MycelarSessionResolver;
import io.mycelar.springai.tool.MycelarCapabilityAuthorizer;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(ChatClient.class)
@ConditionalOnProperty(prefix = "mycelar.spring-ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MycelarSpringAiProperties.class)
class MycelarAutoConfiguration {

    @Bean
    @ConditionalOnBean(MycelarGraph.class)
    @ConditionalOnMissingBean(MycelarRuntime.class)
    MycelarRuntime mycelarRuntime(
            MycelarGraph graph,
            List<ConstraintEvaluator> evaluators,
            ObjectProvider<SemanticMatcher> matcher,
            ObjectProvider<RouteScorer> scorer,
            ObjectProvider<RoutingLimits> limits) {
        MycelarRuntime.Builder builder = MycelarRuntime.builder().graph(graph);
        matcher.ifAvailable(builder::semanticMatcher);
        scorer.ifAvailable(builder::routeScorer);
        limits.ifAvailable(builder::routingLimits);
        if (!evaluators.isEmpty()) {
            builder.constraintEvaluators(evaluators);
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnBean(MycelarRuntime.class)
    @ConditionalOnMissingBean
    MycelarContextRenderer mycelarContextRenderer(MycelarSpringAiProperties properties) {
        return new MycelarContextRenderer(
                properties.isIncludeCapabilitiesInContext(), properties.isIncludeBlockedInContext());
    }

    @Bean
    @ConditionalOnBean(MycelarRuntime.class)
    @ConditionalOnMissingBean
    CapabilityToolRegistry capabilityToolRegistry(
            MycelarRuntime runtime,
            MycelarSpringAiProperties properties,
            ObjectProvider<CapabilityToolBindings> bindings,
            ObjectProvider<ToolCallback> callbacks,
            ObjectProvider<ToolCallbackProvider> providers) {
        return new CapabilityToolRegistry(
                runtime,
                bindings.orderedStream().toList(),
                callbacks.orderedStream().toList(),
                providers.orderedStream().toList(),
                properties.getTools().getMappings());
    }

    @Bean
    @ConditionalOnBean(MycelarRuntime.class)
    @ConditionalOnMissingBean
    MycelarCapabilityAuthorizer mycelarCapabilityAuthorizer() {
        return new MycelarCapabilityAuthorizer();
    }

    @Bean
    @ConditionalOnBean(MycelarRuntime.class)
    @ConditionalOnMissingBean
    MycelarRequestMapper mycelarRequestMapper() {
        return new MycelarRequestMapper();
    }

    @Bean
    @ConditionalOnBean(MycelarRuntime.class)
    @ConditionalOnMissingBean
    MycelarSessionResolver mycelarSessionResolver() {
        return new MycelarSessionResolver();
    }

    @Bean
    @ConditionalOnBean(MycelarRuntime.class)
    @ConditionalOnMissingBean
    MycelarAdvisor mycelarAdvisor(
            MycelarRuntime runtime,
            MycelarRequestMapper requestMapper,
            MycelarContextRenderer renderer,
            CapabilityToolRegistry registry,
            MycelarCapabilityAuthorizer authorizer,
            MycelarSessionResolver sessionResolver,
            MycelarSpringAiProperties properties) {
        return new MycelarAdvisor(
                runtime, requestMapper, renderer, registry, authorizer, sessionResolver, properties);
    }

    @Bean
    @ConditionalOnClass(ChatClientBuilderCustomizer.class)
    @ConditionalOnBean(MycelarAdvisor.class)
    ChatClientBuilderCustomizer mycelarChatClientBuilderCustomizer(MycelarAdvisor advisor) {
        return builder -> builder.defaultAdvisors(advisor);
    }
}
