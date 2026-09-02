package io.mycelar.routing.springai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mycelar.core.graph.NodeId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InMemoryRoutingEmbeddingIndexTest {

    @Test
    void putFindRemoveClearAndDefensiveCopy() {
        RoutingEmbeddingIndex index = new InMemoryRoutingEmbeddingIndex();
        NodeId id = new NodeId("draft");
        float[] vector = {1f, 2f};
        RouteEmbedding stored = new RouteEmbedding(vector, "abc");
        vector[0] = 9f;
        index.put(id, stored);
        RouteEmbedding found = index.find(id).orElseThrow();
        assertThat(found.fingerprint()).isEqualTo("abc");
        assertThat(found.vector()).containsExactly(1f, 2f);
        found.vector()[0] = 0f;
        assertThat(index.find(id).orElseThrow().vector()).containsExactly(1f, 2f);
        index.remove(id);
        assertThat(index.find(id)).isEmpty();
        index.put(id, stored);
        index.clear();
        assertThat(index.find(id)).isEmpty();
    }

    @Test
    void blankFingerprintRejected() {
        assertThatThrownBy(() -> new RouteEmbedding(new float[] {1f}, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentPutsRemainReadable() throws Exception {
        RoutingEmbeddingIndex index = new InMemoryRoutingEmbeddingIndex();
        NodeId id = new NodeId("n");
        var pool = Executors.newFixedThreadPool(4);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(4);
        for (int i = 0; i < 4; i++) {
            float v = i;
            pool.submit(
                    () -> {
                        start.await();
                        index.put(id, new RouteEmbedding(new float[] {v}, "fp"));
                        done.countDown();
                        return null;
                    });
        }
        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(index.find(id)).isPresent();
        assertThat(index.find(id).orElseThrow().fingerprint()).isEqualTo("fp");
    }
}
