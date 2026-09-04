package com.crest.client.core.hud.widgets;

import com.crest.client.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class PropertySlider {
    
    private final String label;
    private float value;
    private final float min;
    private final float max;
    private final String suffix;
    private boolean dragging;
    private final Animated hoverAnim;
    
    public PropertySlider(String label, float value, float min, float max, String suffix) {
        this.label = label;
        this.value = Math.max(min, Math.min(max, value));
        this.min = min;
        this.max = max;
        this.suffix = suffix;
        this.hoverAnim = new Animated(0f, 8f);
    }
    
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean hover, float delta) {
        hoverAnim.set(hover ? 1f : 0f); hoverAnim.tick(delta);
        float hoverProgress = hoverAnim.get();
        
        int labelY = y;
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(label), x, labelY, Theme.FOREGROUND);
        
        String valueText = String.format("%.0f%s", value, suffix);
        int valueWidth = net.minecraft.client.Minecraft.getInstance().font.width(valueText);
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(valueText), x + width - valueWidth, labelY, Theme.ACCENT);
        
        int sliderY = y + 14;
        int sliderHeight = 4;
        int trackColor = ColorUtil.withAlpha(Theme.BORDER, 200);
        Panel.draw(g, x, sliderY, width, sliderHeight, trackColor);
        
        float progress = (value - min) / (max - min);
        int fillWidth = (int)(width * progress);
        int fillColor = ColorUtil.withAlpha(Theme.ACCENT, 255);
        Panel.draw(g, x, sliderY, fillWidth, sliderHeight, fillColor);
        
        int thumbX = x + fillWidth - 6;
        int thumbY = sliderY - 4;
        int thumbSize = 12;
        int thumbColor = ColorUtil.mix(
            ColorUtil.withAlpha(Theme.ACCENT, 255),
            ColorUtil.withAlpha(Theme.getAnimatedAccent(), 255),
            hoverProgress
        );
        Panel.draw(g, thumbX, thumbY, thumbSize, thumbSize, thumbColor);
        
        if (hoverProgress > 0.01f) {
            int glowColor = ColorUtil.withAlpha(Theme.ACCENT, (int)(100 * hoverProgress));
            Panel.draw(g, thumbX - 2, thumbY - 2, thumbSize + 4, thumbSize + 4, glowColor);
        }
    }
    
    public boolean handleMouseDown(double mouseX, double mouseY, int x, int y, int width) {
        int sliderY = y + 14;
        if (mouseY >= sliderY - 6 && mouseY <= sliderY + 10) {
            dragging = true;
            updateValue(mouseX, x, width);
            return true;
        }
        return false;
    }
    
    public boolean handleMouseDrag(double mouseX, double mouseY, int x, int y, int width) {
        if (dragging) {
            updateValue(mouseX, x, width);
            return true;
        }
        return false;
    }
    
    public void handleMouseUp() {
        dragging = false;
    }
    
    private void updateValue(double mouseX, int x, int width) {
        float progress = Math.max(0f, Math.min(1f, (float)(mouseX - x) / width));
        value = min + progress * (max - min);
    }
    
    public float getValue() {
        return value;
    }
    
    public void setValue(float value) {
        this.value = Math.max(min, Math.min(max, value));
    }
    
    public boolean isDragging() {
        return dragging;
    }
}
