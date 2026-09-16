package com.crest.client.core.mixin;

import com.crest.client.catstrokes.CatHud;
import com.crest.client.catstrokes.CatStrokesConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Matches the standalone bongocat-mod's injection point exactly: GPU work
 * (FBO render + readPixels + DynamicTexture upload + graphics.blit) runs at
 * {@code extractRenderState} RETURN — after the entire GUI state extraction is
 * complete, so raw GL cannot corrupt the in-progress extraction.
 */
@Mixin(Gui.class)
public class GuiExtractMixin {

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void bongocat$extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        CatHud hud = CatHud.instance();
        hud.init();
        hud.extract(mc, graphics, deltaTracker);
    }
}
