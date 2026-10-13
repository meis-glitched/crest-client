package com.crest.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CrestChunkManager {
    
    private static final CrestChunkManager INSTANCE = new CrestChunkManager();
    
    private final Map<Long, Long> chunkHandles = new ConcurrentHashMap<>();
    
    private CrestChunkManager() {}
    
    public static CrestChunkManager getInstance() {
        return INSTANCE;
    }
    
    public void meshChunk(int chunkX, int chunkY, int chunkZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        
        Level level = mc.level;
        LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        
        if (chunk == null) return;
        
        int sectionIndex = level.getSectionIndex(chunkY << 4);
        if (sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()) return;
        
        LevelChunkSection section = chunk.getSection(sectionIndex);
        if (section.hasOnlyAir()) return;
        
        byte[] blockData = extractBlockData(section);
        
        long handle = CrestRenderNative.nativeMeshChunk(chunkX, chunkY, chunkZ, blockData);
        
        if (handle >= 0) {
            long chunkKey = packChunkPos(chunkX, chunkY, chunkZ);
            
            Long oldHandle = chunkHandles.put(chunkKey, handle);
            if (oldHandle != null) {
                CrestRenderNative.nativeUnloadChunk(oldHandle);
            }
        }
    }
    
    private byte[] extractBlockData(LevelChunkSection section) {
        ByteBuffer buffer = ByteBuffer.allocate(16 * 16 * 16 * 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    short blockId = getBlockId(state);
                    buffer.putShort(blockId);
                }
            }
        }
        
        return buffer.array();
    }
    
    private short getBlockId(BlockState state) {
        if (state.isAir()) {
            return 0;
        }
        
        return (short) (net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getId(state.getBlock()) & 0xFFFF);
    }
    
    public Long getChunkHandle(int chunkX, int chunkY, int chunkZ) {
        long key = packChunkPos(chunkX, chunkY, chunkZ);
        return chunkHandles.get(key);
    }
    
    public void unloadChunk(int chunkX, int chunkY, int chunkZ) {
        long key = packChunkPos(chunkX, chunkY, chunkZ);
        Long handle = chunkHandles.remove(key);
        
        if (handle != null) {
            CrestRenderNative.nativeUnloadChunk(handle);
        }
    }
    
    public void clear() {
        for (Long handle : chunkHandles.values()) {
            CrestRenderNative.nativeUnloadChunk(handle);
        }
        chunkHandles.clear();
    }
    
    private long packChunkPos(int x, int y, int z) {
        return ((long) x << 42) | ((long) y << 21) | z;
    }
}
