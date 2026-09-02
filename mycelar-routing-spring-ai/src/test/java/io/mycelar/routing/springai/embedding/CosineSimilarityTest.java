package io.mycelar.routing.springai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class CosineSimilarityTest {

    private final VectorSimilarity cosine = new CosineSimilarity();

    @Test
    void identicalUnitVectorsAreOne() {
        assertThat(cosine.similarity(new float[] {1f, 0f}, new float[] {1f, 0f})).isEqualTo(1.0);
    }

    @Test
    void orthogonalVectorsAreZero() {
        assertThat(cosine.similarity(new float[] {1f, 0f}, new float[] {0f, 1f})).isEqualTo(0.0);
    }

    @Test
    void oppositeVectorsAreMinusOne() {
        assertThat(cosine.similarity(new float[] {1f, 0f}, new float[] {-1f, 0f})).isEqualTo(-1.0);
    }

    @Test
    void zeroVectorIsZero() {
        assertThat(cosine.similarity(new float[] {0f, 0f}, new float[] {1f, 0f})).isEqualTo(0.0);
        assertThat(cosine.similarity(new float[] {}, new float[] {})).isEqualTo(0.0);
    }

    @Test
    void lengthMismatchThrowsIae() {
        assertThatThrownBy(() -> cosine.similarity(new float[] {1f}, new float[] {1f, 0f}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fortyFiveDegreesIsAboutOneOverSqrtTwo() {
        assertThat(cosine.similarity(new float[] {1f, 0f}, new float[] {1f, 1f}))
                .isCloseTo(1.0 / Math.sqrt(2.0), within(1e-6));
    }
}
