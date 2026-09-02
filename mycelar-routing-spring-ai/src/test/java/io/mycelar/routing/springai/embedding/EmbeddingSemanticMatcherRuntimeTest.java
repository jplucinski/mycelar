package io.mycelar.routing.springai.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import io.mycelar.core.graph.NodeId;
import io.mycelar.core.routing.DefaultRouteScorer;
import io.mycelar.core.routing.MycelarRuntime;
import io.mycelar.core.routing.RouteRequest;
import io.mycelar.core.routing.RoutingContext;
import io.mycelar.core.routing.RoutingLimits;
import io.mycelar.core.routing.ScoringWeights;
import io.mycelar.routing.springai.fixture.DetailGraphFixture;
import io.mycelar.routing.springai.fixture.MapEmbeddingModel;
import org.junit.jupiter.api.Test;

class EmbeddingSemanticMatcherRuntimeTest {

    @Test
    void runtimeSelectsDraftEmailForFollowUpIntent() {
        var graph = DetailGraphFixture.graph();
        var encoder = new DefaultNodeRoutingTextEncoder();
        MapEmbeddingModel model = DetailGraphFixture.model(encoder);
        var matcher =
                new EmbeddingSemanticMatcher(
                        model,
                        new InMemoryRoutingEmbeddingIndex(),
                        encoder,
                        new Sha256RoutingTextFingerprint(),
                        new CosineSimilarity(),
                        true,
                        EmbeddingFailureMode.IGNORE_SIGNAL);
        var scorer = new DefaultRouteScorer(matcher, new ScoringWeights(0.8, 0.2, 0.0, 0.0), RoutingLimits.defaults());
        var runtime = MycelarRuntime.builder().graph(graph).semanticMatcher(matcher).routeScorer(scorer).build();
        var result =
                runtime.route(
                        RouteRequest.builder()
                                .intent("write a follow-up email")
                                .context(RoutingContext.empty())
                                .topK(1)
                                .build());
        assertThat(result.primary()).isPresent();
        assertThat(result.primary().orElseThrow().path().nodeIds())
                .last()
                .isEqualTo(new NodeId("draft-email"));
        assertThat(result.primary().orElseThrow().score().components()).containsKey("semantic");
    }
}
