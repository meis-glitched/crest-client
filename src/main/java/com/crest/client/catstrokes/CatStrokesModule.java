package com.crest.client.catstrokes;

import com.crest.client.core.HudModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class CatStrokesModule extends HudModule {
    public CatStrokesModule() {
        super(-1, -1);
    }

    @Override
    public String getId() { return "bango_cat"; }

    @Override
    public String getName() { return "BangoCat"; }

    @Override
    public String getCategory() { return "HUD"; }

    @Override
    public String getDescription() { return "Live2D Bango Cat that reacts to your keyboard and mouse input."; }

    @Override
    public int getWidth() {
        return Math.max(1, Math.round(CatRenderer.W * CatStrokesConfig.getInstance().scale));
    }

    @Override
    public int getHeight() {
        return Math.max(1, Math.round(CatRenderer.H * CatStrokesConfig.getInstance().scale));
    }

    @Override
    public void renderScaled(GuiGraphicsExtractor g, Minecraft mc, DeltaTracker d) {
        render(g, mc, d);
    }

    @Override
    public void render(GuiGraphicsExtractor g, Minecraft mc, DeltaTracker d) {
        if (mc.getWindow() == null) return;
        int dw = getRenderWidth();
        int dh = getRenderHeight();
        boolean overridden = getOverrideW() != null || getOverrideH() != null;
        if (!overridden) {
            int gw = mc.getWindow().getGuiScaledWidth();
            int gh = mc.getWindow().getGuiScaledHeight();
            if (gw > 0 && gh > 0) {
                float k = Math.min(1f, Math.min((gw * 0.45f) / dw, (gh * 0.38f) / dh));
                if (k < 1f) {
                    dw = Math.max(1, Math.round(dw * k));
                    dh = Math.max(1, Math.round(dh * k));
                }
            }
        }
        int bx, by;
        if (x < 0) {
            bx = Math.max(0, (mc.getWindow().getGuiScaledWidth() - dw) / 2);
            by = Math.max(0, mc.getWindow().getGuiScaledHeight() - dh - 4);
        } else {
            bx = x;
            by = y;
        }
        CatHud.instance().storeRect(bx, by, dw, dh);
    }
}