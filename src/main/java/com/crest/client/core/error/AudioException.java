package com.crest.client.core.error;

public class AudioException extends CrestException {
    public AudioException(String message) {
        super(message);
    }
    
    public AudioException(String message, Throwable cause) {
        super(message, cause);
    }
}
