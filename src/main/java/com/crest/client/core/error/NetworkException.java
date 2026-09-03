package com.crest.client.core.error;

public class NetworkException extends CrestException {
    public NetworkException(String message) {
        super(message);
    }
    
    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
