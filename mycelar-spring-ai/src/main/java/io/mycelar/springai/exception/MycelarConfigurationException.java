package io.mycelar.springai.exception;

public final class MycelarConfigurationException extends RuntimeException {
    public MycelarConfigurationException(String message) {
        super(message);
    }

    public MycelarConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
