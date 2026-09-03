package com.crest.client.core.concurrent;

import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;

import java.util.concurrent.*;

public class CrestExecutors {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("Executors");
    
    private static ExecutorService ioPool;
    private static ExecutorService networkPool;
    private static ExecutorService audioPool;
    private static ExecutorService computePool;
    private static ScheduledExecutorService scheduledPool;
    
    public static synchronized void initialize() {
        if (ioPool != null) return;
        
        LOGGER.info("Initializing thread pools");
        
        ioPool = createPool("Crest-IO", 2, 4);
        networkPool = createPool("Crest-Network", 2, 8);
        audioPool = createPool("Crest-Audio", 1, 2);
        computePool = createPool("Crest-Compute", 2, Runtime.getRuntime().availableProcessors());
        scheduledPool = Executors.newScheduledThreadPool(2, new CrestThreadFactory("Crest-Scheduled"));
        
        LOGGER.info("Thread pools initialized");
    }
    
    public static ExecutorService io() {
        if (ioPool == null) initialize();
        return ioPool;
    }
    
    public static ExecutorService network() {
        if (networkPool == null) initialize();
        return networkPool;
    }
    
    public static ExecutorService audio() {
        if (audioPool == null) initialize();
        return audioPool;
    }
    
    public static ExecutorService compute() {
        if (computePool == null) initialize();
        return computePool;
    }
    
    public static ScheduledExecutorService scheduled() {
        if (scheduledPool == null) initialize();
        return scheduledPool;
    }
    
    public static void shutdown() {
        LOGGER.info("Shutting down thread pools");
        shutdownPool(ioPool, "IO");
        shutdownPool(networkPool, "Network");
        shutdownPool(audioPool, "Audio");
        shutdownPool(computePool, "Compute");
        shutdownPool(scheduledPool, "Scheduled");
        LOGGER.info("All thread pools shut down");
    }
    
    private static ExecutorService createPool(String name, int coreSize, int maxSize) {
        return new ThreadPoolExecutor(
            coreSize,
            maxSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new CrestThreadFactory(name),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
    
    private static void shutdownPool(ExecutorService pool, String name) {
        if (pool == null) return;
        try {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("{} pool did not terminate gracefully, forcing shutdown", name);
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted while shutting down {} pool", e, name);
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    private static class CrestThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final ThreadGroup group;
        private int threadNumber = 0;
        
        CrestThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
            this.group = new ThreadGroup(namePrefix + "-Group");
        }
        
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + "-" + threadNumber++);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            t.setUncaughtExceptionHandler((thread, throwable) -> 
                LOGGER.error("Uncaught exception in thread {}", throwable, thread.getName())
            );
            return t;
        }
    }
}
