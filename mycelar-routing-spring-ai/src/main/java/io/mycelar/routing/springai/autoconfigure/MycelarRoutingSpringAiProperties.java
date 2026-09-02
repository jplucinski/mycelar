package io.mycelar.routing.springai.autoconfigure;

import io.mycelar.routing.springai.embedding.EmbeddingFailureMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mycelar.routing.spring-ai")
public class MycelarRoutingSpringAiProperties {

    private boolean enabled = true;
    private final Embedding embedding = new Embedding();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public static class Embedding {
        private Indexing indexing = Indexing.LAZY;
        private boolean normalizeScore = true;
        private EmbeddingFailureMode failureMode = EmbeddingFailureMode.IGNORE_SIGNAL;

        public Indexing getIndexing() {
            return indexing;
        }

        public void setIndexing(Indexing indexing) {
            this.indexing = indexing;
        }

        public boolean isNormalizeScore() {
            return normalizeScore;
        }

        public void setNormalizeScore(boolean normalizeScore) {
            this.normalizeScore = normalizeScore;
        }

        public EmbeddingFailureMode getFailureMode() {
            return failureMode;
        }

        public void setFailureMode(EmbeddingFailureMode failureMode) {
            this.failureMode = failureMode;
        }

        public enum Indexing {
            LAZY,
            EAGER
        }
    }
}
