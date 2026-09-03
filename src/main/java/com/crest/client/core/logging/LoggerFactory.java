package com.crest.client.core.logging;

import java.util.concurrent.ConcurrentHashMap;

public class LoggerFactory {
    private static final ConcurrentHashMap<String, CrestLogger> loggers = new ConcurrentHashMap<>();
    
    public static CrestLogger getLogger(Class<?> clazz) {
        return loggers.computeIfAbsent(clazz.getName(), k -> new CrestLogger(clazz));
    }
    
    public static CrestLogger getLogger(String name) {
        return loggers.computeIfAbsent(name, CrestLogger::new);
    }
}
