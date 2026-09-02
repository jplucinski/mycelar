package io.mycelar.routing.springai.exception;

public class EmbeddingDimensionMismatchException extends RuntimeException {
    public EmbeddingDimensionMismatchException(String message) {
        super(message);
    }
}
