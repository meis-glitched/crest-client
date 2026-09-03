package com.crest.client.core.cache;

import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BufferPool {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("BufferPool");
    
    private final Queue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();
    private final int bufferSize;
    private final int maxPoolSize;
    private final boolean direct;
    private int allocated = 0;
    
    public BufferPool(int bufferSize, int maxPoolSize, boolean direct) {
        this.bufferSize = bufferSize;
        this.maxPoolSize = maxPoolSize;
        this.direct = direct;
        LOGGER.debug("Created buffer pool: size={}, max={}, direct={}", bufferSize, maxPoolSize, direct);
    }
    
    public ByteBuffer acquire() {
        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            allocated++;
            buffer = direct ? ByteBuffer.allocateDirect(bufferSize) : ByteBuffer.allocate(bufferSize);
            if (allocated % 100 == 0) {
                LOGGER.debug("Allocated {} buffers (pool size: {})", allocated, pool.size());
            }
        }
        buffer.clear();
        return buffer;
    }
    
    public void release(ByteBuffer buffer) {
        if (buffer == null) return;
        if (pool.size() < maxPoolSize) {
            buffer.clear();
            pool.offer(buffer);
        }
    }
    
    public void clear() {
        pool.clear();
        allocated = 0;
        LOGGER.debug("Buffer pool cleared");
    }
    
    public int getPoolSize() {
        return pool.size();
    }
    
    public int getAllocated() {
        return allocated;
    }
}
