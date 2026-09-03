package com.crest.client.core.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class CrestLogger {
    private final Logger logger;
    private final String context;
    
    private static final Marker MODULE_MARKER = MarkerFactory.getMarker("MODULE");
    private static final Marker NETWORK_MARKER = MarkerFactory.getMarker("NETWORK");
    private static final Marker STORAGE_MARKER = MarkerFactory.getMarker("STORAGE");
    private static final Marker AUDIO_MARKER = MarkerFactory.getMarker("AUDIO");
    private static final Marker SECURITY_MARKER = MarkerFactory.getMarker("SECURITY");
    
    public CrestLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
        this.context = clazz.getSimpleName();
    }
    
    public CrestLogger(String name) {
        this.logger = LoggerFactory.getLogger("Crest." + name);
        this.context = name;
    }
    
    public void info(String message) {
        logger.info("[{}] {}", context, message);
    }
    
    public void info(String message, Object... args) {
        logger.info("[{}] " + message, prependContext(args));
    }
    
    public void warn(String message) {
        logger.warn("[{}] {}", context, message);
    }
    
    public void warn(String message, Object... args) {
        logger.warn("[{}] " + message, prependContext(args));
    }
    
    public void error(String message) {
        logger.error("[{}] {}", context, message);
    }
    
    public void error(String message, Throwable throwable) {
        logger.error("[{}] {}", context, message, throwable);
    }
    
    public void error(String message, Object... args) {
        logger.error("[{}] " + message, prependContext(args));
    }
    
    public void debug(String message) {
        logger.debug("[{}] {}", context, message);
    }
    
    public void debug(String message, Object... args) {
        logger.debug("[{}] " + message, prependContext(args));
    }
    
    public void module(String message, Object... args) {
        logger.info(MODULE_MARKER, "[{}] " + message, prependContext(args));
    }
    
    public void network(String message, Object... args) {
        logger.info(NETWORK_MARKER, "[{}] " + message, prependContext(args));
    }
    
    public void storage(String message, Object... args) {
        logger.info(STORAGE_MARKER, "[{}] " + message, prependContext(args));
    }
    
    public void audio(String message, Object... args) {
        logger.info(AUDIO_MARKER, "[{}] " + message, prependContext(args));
    }
    
    public void security(String message, Object... args) {
        logger.warn(SECURITY_MARKER, "[{}] " + message, prependContext(args));
    }
    
    private Object[] prependContext(Object[] args) {
        Object[] result = new Object[args.length + 1];
        result[0] = context;
        System.arraycopy(args, 0, result, 1, args.length);
        return result;
    }
}
