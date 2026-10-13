package com.crest.client.core.mixin;

import com.crest.client.core.CrestModules;
import com.crest.client.render.CrestRenderModule;
import com.crest.client.render.CrestRenderNative;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class CrestRenderChunkMixin {
    
    private static boolean rendererInitialized = false;
    
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void crest$initRenderer(
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
            }
        }
    }
    
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", 
        target = "Lnet/minecraft/client/renderer/chunk/SectionCompiler;uploadChunkLayer(Lnet/minecraft/client/renderer/chunk/SectionBufferBuilderPack;Lnet/minecraft/world/level/chunk/LevelChunkSection;)V",
        shift = At.Shift.BEFORE))
    private void crest$interceptChunkRender(
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
    }
}
