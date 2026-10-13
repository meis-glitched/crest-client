package com.crest.client.core.mixin;

import com.crest.client.core.CrestModules;
import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;
import com.crest.client.render.CrestChunkManager;
import com.crest.client.render.CrestRenderNative;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;

@Mixin(LevelRenderer.class)
public class CrestRenderChunkMixin {
    
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("CrestRender");
    private static boolean rendererInitialized = false;
    private static int frameCount = 0;
    private static final Set<Long> processedChunks = new HashSet<>();
    
    @Shadow
    private Minecraft minecraft;
    
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void crest$initAndMeshChunks(
        com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator,
        net.minecraft.client.DeltaTracker delta,
        boolean bl,
        CameraRenderState cameraRenderState,
        Matrix4fc projection,
        com.mojang.blaze3d.buffers.GpuBufferSlice slice,
        org.joml.Vector4f vec,
        boolean bl2,
        net.minecraft.client.renderer.chunk.ChunkSectionsToRender chunks,
        CallbackInfo ci) {
        
        if (!CrestModules.isEnabled("crestrender")) {
            return;
        }
        
        if (!rendererInitialized) {
            if (CrestRenderNative.nativeInitRenderer()) {
                rendererInitialized = true;
                LOGGER.info("OpenGL renderer initialized successfully");
            } else {
                LOGGER.error("Failed to initialize OpenGL renderer");
                return;
            }
        }
        
        frameCount++;
        
        if (frameCount % 20 == 0 && minecraft.level != null) {
            processChunksAroundPlayer();
        }
    }
    
    private void processChunksAroundPlayer() {
        if (minecraft.player == null || minecraft.level == null) return;
        
        Level level = minecraft.level;
        int playerChunkX = minecraft.player.chunkPosition().x();
        int playerChunkZ = minecraft.player.chunkPosition().z();
        int playerY = (int) minecraft.player.getY();
        
        int radius = 4;
        int processed = 0;
        
        int minSection = level.getMinSectionY() >> 4;
        int maxSection = (level.getMaxSectionY() >> 4) - 1;
        
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;
                
                for (int sectionY = Math.max(minSection, (playerY >> 4) - 2); 
                     sectionY <= Math.min(maxSection, (playerY >> 4) + 2); 
                     sectionY++) {
                    
                    long chunkKey = packChunkPos(chunkX, sectionY, chunkZ);
                    
                    if (!processedChunks.contains(chunkKey)) {
                        try {
                            CrestChunkManager.getInstance().meshChunk(chunkX, sectionY, chunkZ);
                            processedChunks.add(chunkKey);
                            processed++;
                            
                            if (processed >= 8) {
                                return;
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to mesh chunk ({}, {}, {}): {}", 
                                chunkX, sectionY, chunkZ, e.getMessage());
                        }
                    }
                }
            }
        }
    }
    
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", 
        target = "Lnet/minecraft/client/renderer/chunk/SectionCompiler;renderChunkSections(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
        ordinal = 0))
    private void crest$drawChunks(
        com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator,
        net.minecraft.client.DeltaTracker delta,
        boolean bl,
        CameraRenderState cameraRenderState,
        Matrix4fc projection,
        com.mojang.blaze3d.buffers.GpuBufferSlice slice,
        org.joml.Vector4f vec,
        boolean bl2,
        net.minecraft.client.renderer.chunk.ChunkSectionsToRender chunks,
        CallbackInfo ci) {
        
        if (!CrestModules.isEnabled("crestrender") || !rendererInitialized) {
            return;
        }
        
        drawCrestChunks(projection, cameraRenderState);
    }
    
    private void drawCrestChunks(Matrix4fc projection, CameraRenderState camera) {
        if (minecraft.player == null || minecraft.level == null) return;
        
        Level level = minecraft.level;
        int playerChunkX = minecraft.player.chunkPosition().x();
        int playerChunkZ = minecraft.player.chunkPosition().z();
        int playerY = (int) minecraft.player.getY();
        
        ByteBuffer projBuffer = ByteBuffer.allocate(64);
        projBuffer.order(ByteOrder.LITTLE_ENDIAN);
        projection.get(projBuffer.asFloatBuffer());
        
        ByteBuffer viewBuffer = ByteBuffer.allocate(64);
        viewBuffer.order(ByteOrder.LITTLE_ENDIAN);
        camera.viewRotationMatrix.get(viewBuffer.asFloatBuffer());
        
        ByteBuffer modelBuffer = ByteBuffer.allocate(64);
        modelBuffer.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 16; i++) {
            modelBuffer.putFloat(i == 0 || i == 5 || i == 10 || i == 15 ? 1.0f : 0.0f);
        }
        
        int radius = 4;
        int drawn = 0;
        
        int minSection = level.getMinSectionY() >> 4;
        int maxSection = (level.getMaxSectionY() >> 4) - 1;
        
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;
                
                for (int sectionY = Math.max(minSection, (playerY >> 4) - 2); 
                     sectionY <= Math.min(maxSection, (playerY >> 4) + 2); 
                     sectionY++) {
                    
                    Long handle = CrestChunkManager.getInstance().getChunkHandle(chunkX, sectionY, chunkZ);
                    
                    if (handle != null) {
                        try {
                            CrestRenderNative.nativeDrawChunk(handle, 
                                projBuffer.array(), viewBuffer.array(), modelBuffer.array());
                            drawn++;
                        } catch (Exception e) {
                        }
                    }
                }
            }
        }
        
        if (frameCount % 100 == 0 && drawn > 0) {
            LOGGER.info("Drew {} CrestRender chunks", drawn);
        }
    }
    
    private long packChunkPos(int x, int y, int z) {
        return ((long) x << 42) | ((long) y << 21) | z;
    }
}

