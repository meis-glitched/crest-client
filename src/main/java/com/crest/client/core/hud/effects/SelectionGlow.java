package com.crest.client.core.hud.effects;

import com.crest.client.ui.*;
import com.crest.client.core.HudModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class SelectionGlow {
    
    private final Animated glowPulse;
    private final Animated glowIntensity;
    
    public SelectionGlow() {
        this.glowPulse = new Animated(0f, 15f);
        this.glowIntensity = new Animated(0f, 10f);
    }
    
    public void render(GuiGraphicsExtractor g, HudModule module, int x, int y, int width, int height, float delta, boolean isSelected) {
        glowIntensity.set(isSelected ? 1f : 0f); glowIntensity.tick(delta);
        float intensity = glowIntensity.get();
        
        if (intensity < 0.01f) return;
        
        glowPulse.set(glowPulse.get() + 0.05f);
        glowPulse.tick(delta);
        if (glowPulse.get() > 1f) glowPulse.set(0f);
        
        float pulse = (float)Math.sin(glowPulse.get() * Math.PI * 2) * 0.5f + 0.5f;
        
        int baseAlpha = (int)(150 * intensity);
        int pulseAlpha = (int)(100 * intensity * pulse);
        
        int glowColor = ColorUtil.withAlpha(Theme.ACCENT, baseAlpha + pulseAlpha);
        
        renderGlowBox(g, x - 2, y - 2, width + 4, height + 4, glowColor, 8);
        renderGlowBox(g, x - 4, y - 4, width + 8, height + 8, ColorUtil.withAlpha(glowColor, glowColor >>> 25), 12);
        renderGlowBox(g, x - 6, y - 6, width + 12, height + 12, ColorUtil.withAlpha(glowColor, glowColor >>> 26), 16);
        
        int borderColor = ColorUtil.withAlpha(Theme.ACCENT, (int)(255 * intensity));
        Panel.drawHollowRect(g, x - 1, y - 1, width + 2, height + 2, borderColor);
        
        int cornerSize = 8;
        renderCorner(g, x - 1, y - 1, cornerSize, true, true, borderColor);
        renderCorner(g, x + width - cornerSize + 1, y - 1, cornerSize, false, true, borderColor);
        renderCorner(g, x - 1, y + height - cornerSize + 1, cornerSize, true, false, borderColor);
        renderCorner(g, x + width - cornerSize + 1, y + height - cornerSize + 1, cornerSize, false, false, borderColor);
    }
    
    private void renderGlowBox(GuiGraphicsExtractor g, int x, int y, int width, int height, int color, int blur) {
        Panel.draw(g, x, y, width, height, color);
    }
    
    private void renderCorner(GuiGraphicsExtractor g, int x, int y, int size, boolean left, boolean top, int color) {
        int thickness = 2;
        if (left) {
            g.fill(x, y, x + thickness, y + size, color);
        } else {
            g.fill(x + size - thickness, y, x + size, y + size, color);
        }
        if (top) {
            g.fill(x, y, x + size, y + thickness, color);
        } else {
            g.fill(x, y + size - thickness, x + size, y + size, color);
        }
    }
    
    public void renderResizeHandles(GuiGraphicsExtractor g, int x, int y, int width, int height, float delta, boolean isSelected) {
        if (!isSelected) return;
        
        int handleSize = 8;
        int handleColor = ColorUtil.withAlpha(Theme.ACCENT, 255);
        int handleOutline = ColorUtil.withAlpha(0xFFFFFF, 255);
        
        // Bottom-right corner -> diagonal resize (both axes)
        // Bottom-center -> height only
        // Right-center -> width only
        int[][] handles = {
            {x + width - handleSize/2, y + height - handleSize/2},
            {x + width/2 - handleSize/2, y + height - handleSize/2},
            {x + width - handleSize/2, y + height/2 - handleSize/2}
        };
        
        for (int[] handle : handles) {
            Panel.draw(g, handle[0] - 1, handle[1] - 1, handleSize + 2, handleSize + 2, handleOutline);
            Panel.draw(g, handle[0], handle[1], handleSize, handleSize, handleColor);
        }
    }
}
