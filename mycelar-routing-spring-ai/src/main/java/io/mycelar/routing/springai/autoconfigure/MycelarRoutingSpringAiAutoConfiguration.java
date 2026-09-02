package io.mycelar.routing.springai.autoconfigure;

import io.mycelar.core.graph.MycelarGraph;
import io.mycelar.core.routing.SemanticMatcher;
import io.mycelar.routing.springai.embedding.EmbeddingSemanticMatcher;
import io.mycelar.routing.springai.embedding.NodeRoutingTextEncoder;
import io.mycelar.routing.springai.embedding.RoutingEmbeddingIndex;
import io.mycelar.routing.springai.embedding.RoutingTextFingerprint;
import io.mycelar.routing.springai.embedding.VectorSimilarity;
import io.mycelar.routing.springai.embedding.RoutingEmbeddingInitializer;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(EmbeddingModel.class)
@ConditionalOnBean(EmbeddingModel.class)
@ConditionalOnProperty(
        prefix = "mycelar.routing.spring-ai",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(MycelarRoutingSpringAiProperties.class)
class MycelarRoutingSpringAiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    NodeRoutingTextEncoder nodeRoutingTextEncoder() {
        return NodeRoutingTextEncoder.canonical();
    }

    @Bean
    @ConditionalOnMissingBean
    RoutingTextFingerprint routingTextFingerprint() {
        return RoutingTextFingerprint.sha256();
    }

    @Bean
    @ConditionalOnMissingBean
    VectorSimilarity vectorSimilarity() {
        return VectorSimilarity.cosine();
    }

    @Bean
    @ConditionalOnMissingBean
    RoutingEmbeddingIndex routingEmbeddingIndex() {
        return RoutingEmbeddingIndex.inMemory();
    }

    @Bean
    @ConditionalOnMissingBean
    SemanticMatcher embeddingSemanticMatcher(
            EmbeddingModel embeddingModel,
            RoutingEmbeddingIndex index,
            NodeRoutingTextEncoder encoder,
            RoutingTextFingerprint fingerprint,
            VectorSimilarity vectorSimilarity,
            MycelarRoutingSpringAiProperties properties) {
        return new EmbeddingSemanticMatcher(
                embeddingModel,
                index,
                encoder,
                fingerprint,
                vectorSimilarity,
                properties.getEmbedding().isNormalizeScore(),
                properties.getEmbedding().getFailureMode());
    }

    @Bean
    @ConditionalOnMissingBean
    RoutingEmbeddingInitializer routingEmbeddingInitializer(
            EmbeddingModel embeddingModel,
            RoutingEmbeddingIndex index,
            NodeRoutingTextEncoder encoder,
            RoutingTextFingerprint fingerprint,
            MycelarRoutingSpringAiProperties properties) {
        return new RoutingEmbeddingInitializer(
                embeddingModel,
                index,
                encoder,
                fingerprint,
                properties.getEmbedding().getFailureMode());
    }

    @Bean
    @ConditionalOnBean(MycelarGraph.class)
    @ConditionalOnProperty(
            prefix = "mycelar.routing.spring-ai.embedding",
            name = "indexing",
            havingValue = "eager")
    ApplicationRunner mycelarRoutingEmbeddingEagerInit(
            RoutingEmbeddingInitializer initializer, MycelarGraph graph) {
        return args -> initializer.initialize(graph);
    }
}
