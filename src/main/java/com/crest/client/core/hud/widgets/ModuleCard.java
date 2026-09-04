package com.crest.client.core.hud.widgets;

import com.crest.client.ui.*;
import com.crest.client.core.CrestModules;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ModuleCard {
    
    private final String moduleId;
    private final String displayName;
    private final String icon;
    private final boolean enabled;
    private final Animated hoverAnim;
    private final Animated toggleAnim;
    
    public ModuleCard(String moduleId, String displayName, String icon, boolean enabled) {
        this.moduleId = moduleId;
        this.displayName = displayName;
        this.icon = icon;
        this.enabled = enabled;
        this.hoverAnim = new Animated(0f, 8f);
        this.toggleAnim = new Animated(enabled ? 1f : 0f, 8f);
    }
    
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean hover, float delta) {
        hoverAnim.set(hover ? 1f : 0f); hoverAnim.tick(delta);
        toggleAnim.set(enabled ? 1f : 0f); toggleAnim.tick(delta);
        
        float hoverProgress = hoverAnim.get();
        float toggleProgress = toggleAnim.get();
        
        int bgColor = ColorUtil.mix(
            ColorUtil.withAlpha(Theme.CARD, 200),
            ColorUtil.withAlpha(Theme.getAnimatedAccent(), 40),
            hoverProgress
        );
        
        Panel.draw(g, x, y, width, height, bgColor);
        
        if (hoverProgress > 0.01f) {
            int borderColor = ColorUtil.withAlpha(Theme.getAnimatedAccent(), (int)(150 * hoverProgress));
            Panel.drawHollowRect(g, x, y, width, height, borderColor);
        }
        
        int iconX = x + 8;
        int iconY = y + (height - 12) / 2;
        int iconColor = ColorUtil.mix(Theme.MUTED_FOREGROUND, Theme.FOREGROUND, hoverProgress);
        g.text(net.minecraft.client.Minecraft.getInstance().font, net.minecraft.network.chat.Component.literal(icon), iconX, iconY, iconColor);
        
        int textX = iconX + 20;
        int textY = y + (height - net.minecraft.client.Minecraft.getInstance().font.lineHeight) / 2;
        g.text(net.minecraft.client.Minecraft.getInstance().font, net.minecraft.network.chat.Component.literal(displayName), textX, textY, Theme.FOREGROUND);
        
        int toggleX = x + width - 32;
        int toggleY = y + (height - 16) / 2;
        renderToggle(g, toggleX, toggleY, toggleProgress, hoverProgress);
    }
    
    private void renderToggle(GuiGraphicsExtractor g, int x, int y, float progress, float hover) {
        int width = 28;
        int height = 16;
        int radius = 8;
        
        int trackColor = ColorUtil.mix(
            ColorUtil.withAlpha(Theme.BORDER, 200),
            ColorUtil.withAlpha(Theme.ACCENT, 255),
            progress
        );
        
        Panel.draw(g, x, y, width, height, trackColor);
        
        float thumbOffset = progress * (width - height);
        int thumbX = x + (int)thumbOffset + 2;
        int thumbY = y + 2;
        int thumbSize = height - 4;
        
        int thumbColor = ColorUtil.mix(
            ColorUtil.withAlpha(0xFFFFFF, 230),
            ColorUtil.withAlpha(0xFFFFFF, 255),
            hover
        );
        
        Panel.draw(g, thumbX, thumbY, thumbSize, thumbSize, thumbColor);
    }
    
    public String getModuleId() {
        return moduleId;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
}
