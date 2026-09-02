package io.mycelar.routing.springai.embedding;

public interface VectorSimilarity {
    double similarity(float[] left, float[] right);

    static VectorSimilarity cosine() {
        return new CosineSimilarity();
    }
}
