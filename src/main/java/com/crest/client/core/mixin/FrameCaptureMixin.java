package com.crest.client.core.mixin;

import com.crest.client.catstrokes.CatHud;
import com.crest.client.core.FrameCapture;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class FrameCaptureMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void crest$onRenderEnd(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        FrameCapture.onRenderEnd();
        CatHud.maybeDumpScreen(Minecraft.getInstance());
    }
}
