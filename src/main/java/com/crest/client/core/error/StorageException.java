package com.crest.client.core.error;

public class StorageException extends CrestException {
    public StorageException(String message) {
        super(message);
    }
    
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
