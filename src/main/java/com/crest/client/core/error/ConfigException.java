package com.crest.client.core.error;

public class ConfigException extends CrestException {
    public ConfigException(String message) {
        super(message);
    }
    
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
