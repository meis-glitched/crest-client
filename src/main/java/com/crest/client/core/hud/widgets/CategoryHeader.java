package com.crest.client.core.hud.widgets;

import com.crest.client.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class CategoryHeader {
    
    private final String name;
    private final String icon;
    private boolean expanded;
    private final Animated expandAnim;
    private final Animated hoverAnim;
    
    public CategoryHeader(String name, String icon, boolean expanded) {
        this.name = name;
        this.icon = icon;
        this.expanded = expanded;
        this.expandAnim = new Animated(expanded ? 1f : 0f, 10f);
        this.hoverAnim = new Animated(0f, 8f);
    }
    
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean hover, float delta) {
        hoverAnim.set(hover ? 1f : 0f); hoverAnim.tick(delta);
        expandAnim.set(expanded ? 1f : 0f); expandAnim.tick(delta);
        
        float hoverProgress = hoverAnim.get();
        int bgColor = ColorUtil.withAlpha(Theme.CARD, (int)(100 + 50 * hoverProgress));
        Panel.draw(g, x, y, width, height, bgColor);
        
        float expandProgress = expandAnim.get();
        float arrowRotation = expandProgress * 90f;
        
        int arrowX = x + 8;
        int arrowY = y + height / 2;
        renderArrow(g, arrowX, arrowY, arrowRotation);
        
        int iconX = x + 24;
        int iconY = y + (height - 12) / 2;
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(icon), iconX, iconY, Theme.FOREGROUND);
        
        int textX = iconX + 18;
        int textY = y + (height - net.minecraft.client.Minecraft.getInstance().font.lineHeight) / 2;
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(name), textX, textY, Theme.FOREGROUND);
    }
    
    private void renderArrow(GuiGraphicsExtractor g, int x, int y, float rotation) {
        String arrow = "▶";
        
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(arrow), 0, 0, Theme.MUTED_FOREGROUND);
        
    }
    
    public boolean isExpanded() {
        return expanded;
    }
    
    public void toggle() {
        expanded = !expanded;
    }
    
    public String getName() {
        return name;
    }
}
