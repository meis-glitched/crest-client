package com.crest.client.core.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class CacheManager {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("CacheManager");
    
    public static <K, V> Cache<K, V> createCache(String name, int maxSize) {
        return createCache(name, maxSize, -1, null);
    }
    
    public static <K, V> Cache<K, V> createCache(String name, int maxSize, long expireAfter, TimeUnit unit) {
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder()
            .maximumSize(maxSize)
            .recordStats();
        
        if (expireAfter > 0 && unit != null) {
            builder.expireAfterWrite(expireAfter, unit);
        }
        
        Cache<K, V> cache = builder.build();
        LOGGER.debug("Created cache '{}' with max size {}", name, maxSize);
        return cache;
    }
    
    public static <K, V> Cache<K, V> createLRU(String name, int maxSize) {
        return createCache(name, maxSize);
    }
}
