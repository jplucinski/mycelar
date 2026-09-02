package io.mycelar.springai.exception;

public class MycelarConfigurationException extends RuntimeException {
    public MycelarConfigurationException(String message) {
        super(message);
    }

    public MycelarConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
