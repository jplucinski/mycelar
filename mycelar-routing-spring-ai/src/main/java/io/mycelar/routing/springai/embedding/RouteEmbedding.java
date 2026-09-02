package io.mycelar.routing.springai.embedding;

import java.util.Objects;

public record RouteEmbedding(float[] vector, String fingerprint) {

    public RouteEmbedding {
        Objects.requireNonNull(vector, "vector");
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (fingerprint.isBlank()) {
            throw new IllegalArgumentException("fingerprint must not be blank");
        }
        vector = vector.clone();
    }

    @Override
    public float[] vector() {
        return vector.clone();
    }
}
