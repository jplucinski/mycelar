package io.mycelar.routing.springai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

class SpringAiRoutingModuleSmokeTest {

    @Test
    void embeddingModelTypeIsLoadable() {
        assertThat(EmbeddingModel.class.getName())
                .isEqualTo("org.springframework.ai.embedding.EmbeddingModel");
    }
}
