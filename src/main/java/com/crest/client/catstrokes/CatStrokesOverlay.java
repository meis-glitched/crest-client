package com.crest.client.catstrokes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

public class CatStrokesOverlay {
    private static final int ARM_X = 5;
    private static final int ARM_Y = 115;
    private static final int ARM_W = 180;
    private static final int ARM_H = 220;

    private static final int MPAD_X = 5;
    private static final int MPAD_Y = 225;
    private static final int MPAD_W = 170;
    private static final int MPAD_H = 115;

    private static final int HAND_W = 100;
    private static final int HAND_H = 80;
    
    private static float smoothMouseX = 0.5f;
    private static float smoothMouseY = 0.5f;
    private static final float MOUSE_SMOOTHING = 0.15f;

    public static void render(GuiGraphicsExtractor g, Minecraft mc, KeyStateTracker input, int baseX, int baseY, float scale) {
        if (mc.getWindow() == null) return;
        CatStrokesTextures.ensure();
        if (CatStrokesTextures.hasFailed()) return;

        int tw = CatStrokesTextures.TEX_W;
        int th = CatStrokesTextures.TEX_H;

        g.pose().pushMatrix();
        g.pose().translate(baseX, baseY);
        g.pose().scale(scale);
        g.pose().translate(-baseX, -baseY);

        g.blit(RenderPipelines.GUI_TEXTURED, CatStrokesTextures.RIGHTARM,
            baseX + ARM_X, baseY + ARM_Y, 0f, 0f, ARM_W, ARM_H, ARM_W, ARM_H, ARM_W, ARM_H, 0xFFFFFFFF);

        g.blit(RenderPipelines.GUI_TEXTURED, CatStrokesTextures.CATBG,
            baseX, baseY, 0f, 0f, tw, th, tw, th, 0xFFFFFFFF);

        if (input.anyPressed()) {
            for (int i = 0; i < 15; i++) {
                if (input.isPressed(i)) {
                    g.blit(RenderPipelines.GUI_TEXTURED, CatStrokesTextures.KEYBOARD_OVERLAYS[i],
                        baseX, baseY, 0f, 0f, tw, th, tw, th, 0xFFFFFFFF);
                }
            }

            int handIdx = input.getLastPressedIndex();
            if (handIdx >= 0) {
                g.blit(RenderPipelines.GUI_TEXTURED, CatStrokesTextures.LEFTHAND[handIdx],
                    baseX, baseY, 0f, 0f, tw, th, tw, th, 0xFFFFFFFF);
            }
        } else {
            g.blit(RenderPipelines.GUI_TEXTURED, CatStrokesTextures.LEFTUP,
                baseX, baseY, 0f, 0f, tw, th, tw, th, 0xFFFFFFFF);
        }

        double rawX = input.getCursorX();
        double rawY = input.getCursorY();
        int winW = mc.getWindow().getWidth();
        int winH = mc.getWindow().getHeight();
        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();
        
        double guiX = rawX * guiW / winW;
        double guiY = rawY * guiH / winH;
        
        double normX = (guiX - baseX) / (tw * scale);
        double normY = (guiY - baseY) / (th * scale);
        
        normX = Math.max(0.0, Math.min(1.0, normX));
        normY = Math.max(0.0, Math.min(1.0, normY));
        
        smoothMouseX += (float)(normX - smoothMouseX) * MOUSE_SMOOTHING;
        smoothMouseY += (float)(normY - smoothMouseY) * MOUSE_SMOOTHING;
        
        double centerX = MPAD_X + MPAD_W / 2.0;
        double centerY = MPAD_Y + MPAD_H / 2.0;
        
        double offsetX = (smoothMouseX - 0.5) * MPAD_W * 0.9;
        double offsetY = (smoothMouseY - 0.5) * MPAD_H * 0.9;
        
        double radiusX = MPAD_W * 0.45;
        double radiusY = MPAD_H * 0.45;
        double distance = Math.sqrt((offsetX * offsetX) / (radiusX * radiusX) + 
                                   (offsetY * offsetY) / (radiusY * radiusY));
        
        if (distance > 1.0) {
            offsetX /= distance;
            offsetY /= distance;
        }
        
        int handX = (int)(centerX + offsetX);
        int handY = (int)(centerY + offsetY);
        
        float clickScale = 1.0f;
        int clickOffsetY = 0;
        
        if (input.isLeftMouseDown() || input.isRightMouseDown()) {
            clickScale = 0.95f;
            clickOffsetY = 2;
        }
        
        g.pose().pushMatrix();
        g.pose().translate(baseX + handX, baseY + handY + clickOffsetY);
        g.pose().scale(clickScale);
        g.pose().translate(-(baseX + handX), -(baseY + handY + clickOffsetY));
        
        g.blit(RenderPipelines.GUI_TEXTURED, CatStrokesTextures.RIGHTHAND,
            baseX + handX - HAND_W / 2, baseY + handY - HAND_H / 2,
            0f, 0f, HAND_W, HAND_H, HAND_W, HAND_H, HAND_W, HAND_H, 0xFFFFFFFF);
        
        g.pose().popMatrix();

        g.pose().popMatrix();
    }
}
