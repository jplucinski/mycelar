package io.mycelar.springai.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.mycelar.core.graph.MycelarGraph;
import io.mycelar.core.routing.MycelarRuntime;
import io.mycelar.springai.advisor.MycelarAdvisor;
import io.mycelar.springai.fixture.LeadGraphFixture;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MycelarAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(MycelarAutoConfiguration.class));

    @Test
    void graphCreatesRuntimeAndAdvisor() {
        runner.withBean(MycelarGraph.class, LeadGraphFixture::graph)
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(MycelarRuntime.class);
                            assertThat(ctx).hasSingleBean(MycelarAdvisor.class);
                            assertThat(ctx).hasSingleBean(ChatClientBuilderCustomizer.class);
                            assertThat(ctx.getBean(MycelarRuntime.class).graph())
                                    .isSameAs(ctx.getBean(MycelarGraph.class));
                        });
    }

    @Test
    void runtimeOnlyUsesGraphFromRuntime() {
        MycelarGraph graph = LeadGraphFixture.graph();
        MycelarRuntime runtime = MycelarRuntime.builder().graph(graph).build();
        runner.withBean(MycelarRuntime.class, () -> runtime)
                .run(
                        ctx -> {
                            assertThat(ctx).doesNotHaveBean(MycelarGraph.class);
                            assertThat(ctx).hasSingleBean(MycelarRuntime.class);
                            assertThat(ctx).hasSingleBean(MycelarAdvisor.class);
                            assertThat(ctx.getBean(MycelarRuntime.class).graph().capability(
                                            new io.mycelar.core.capability.CapabilityId("read")))
                                    .isPresent();
                        });
    }

    @Test
    void neitherGraphNorRuntimeCreatesNoMycelarBeans() {
        runner.run(
                ctx -> {
                    assertThat(ctx).doesNotHaveBean(MycelarRuntime.class);
                    assertThat(ctx).doesNotHaveBean(MycelarAdvisor.class);
                });
    }

    @Test
    void disabledDoesNotCreateAdvisor() {
        runner.withBean(MycelarGraph.class, LeadGraphFixture::graph)
                .withPropertyValues("mycelar.spring-ai.enabled=false")
                .run(
                        ctx -> {
                            assertThat(ctx).doesNotHaveBean(MycelarAdvisor.class);
                            assertThat(ctx).doesNotHaveBean(MycelarRuntime.class);
                        });
    }
}
