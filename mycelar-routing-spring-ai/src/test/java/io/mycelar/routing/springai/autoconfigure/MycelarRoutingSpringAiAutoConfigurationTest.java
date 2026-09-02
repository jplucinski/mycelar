package io.mycelar.routing.springai.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.mycelar.core.graph.MycelarGraph;
import io.mycelar.core.routing.SemanticMatcher;
import io.mycelar.routing.springai.embedding.EmbeddingSemanticMatcher;
import io.mycelar.routing.springai.fixture.DetailGraphFixture;
import io.mycelar.routing.springai.fixture.MapEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MycelarRoutingSpringAiAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(MycelarRoutingSpringAiAutoConfiguration.class));

    @Test
    void embeddingModelCreatesSemanticMatcher() {
        runner.withBean(MapEmbeddingModel.class, MapEmbeddingModel::new)
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(SemanticMatcher.class);
                            assertThat(ctx.getBean(SemanticMatcher.class))
                                    .isInstanceOf(EmbeddingSemanticMatcher.class);
                        });
    }

    @Test
    void disabledCreatesNoMatcher() {
        runner.withBean(MapEmbeddingModel.class, MapEmbeddingModel::new)
                .withPropertyValues("mycelar.routing.spring-ai.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(SemanticMatcher.class));
    }

    @Test
    void noEmbeddingModelCreatesNoMatcher() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(SemanticMatcher.class));
    }

    @Test
    void userSemanticMatcherWins() {
        SemanticMatcher custom = (intent, node) -> 0.0;
        runner.withBean(MapEmbeddingModel.class, MapEmbeddingModel::new)
                .withBean(SemanticMatcher.class, () -> custom)
                .run(ctx -> assertThat(ctx.getBean(SemanticMatcher.class)).isSameAs(custom));
    }

    @Test
    void eagerIndexingRunsWithoutFailingContext() {
        runner.withBean(MapEmbeddingModel.class, MapEmbeddingModel::new)
                .withBean(MycelarGraph.class, DetailGraphFixture::graph)
                .withPropertyValues("mycelar.routing.spring-ai.embedding.indexing=eager")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(SemanticMatcher.class);
                            assertThat(ctx).hasBean("mycelarRoutingEmbeddingEagerInit");
                        });
    }
}
