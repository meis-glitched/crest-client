package com.crest.client.core.error;

public class CrestException extends RuntimeException {
    public CrestException(String message) {
        super(message);
    }
    
    public CrestException(String message, Throwable cause) {
        super(message, cause);
    }
}
