package com.crest.client.core.hud.editor;

import com.crest.client.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class HudContextMenu {
    
    private static final int WIDTH = 160;
    private static final int ITEM_HEIGHT = 24;
    private static final int PADDING = 4;
    
    private final List<MenuItem> items = new ArrayList<>();
    private int x, y;
    private boolean visible = false;
    private int hoveredIndex = -1;
    private final Animated fadeAnim;
    
    public HudContextMenu() {
        this.fadeAnim = new Animated(0f, 15f);
        initializeItems();
    }
    
    private void initializeItems() {
        items.add(new MenuItem("duplicate", "Duplicate", "📋", true));
        items.add(new MenuItem("delete", "Delete", "🗑️", true));
        items.add(new MenuItem("separator", "", "", false));
        items.add(new MenuItem("front", "Bring to Front", "⬆️", true));
        items.add(new MenuItem("back", "Send to Back", "⬇️", true));
        items.add(new MenuItem("separator", "", "", false));
        items.add(new MenuItem("align_left", "Align Left", "⬅️", true));
        items.add(new MenuItem("align_center", "Align Center", "↔️", true));
        items.add(new MenuItem("align_right", "Align Right", "➡️", true));
        items.add(new MenuItem("separator", "", "", false));
        items.add(new MenuItem("align_top", "Align Top", "⬆️", true));
        items.add(new MenuItem("align_middle", "Align Middle", "↕️", true));
        items.add(new MenuItem("align_bottom", "Align Bottom", "⬇️", true));
        items.add(new MenuItem("separator", "", "", false));
        items.add(new MenuItem("lock", "Lock Position", "🔒", true));
        items.add(new MenuItem("reset", "Reset Position", "↺", true));
    }
    
    public void show(int x, int y) {
        this.x = x;
        this.y = y;
        this.visible = true;
    }
    
    public void hide() {
        this.visible = false;
    }
    
    public boolean isVisible() {
        return visible && fadeAnim.get() > 0.01f;
    }
    
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!visible) {
            fadeAnim.set(0f); fadeAnim.tick(delta);
            return;
        }
        
        fadeAnim.set(1f); fadeAnim.tick(delta);
        float fadeProgress = fadeAnim.get();
        
        if (fadeProgress < 0.01f) return;
        
        int height = calculateHeight();
        
        int bgAlpha = (int)(245 * fadeProgress);
        Panel.draw(g, x, y, WIDTH, height, ColorUtil.withAlpha(Theme.BACKGROUND, 240));
        Panel.drawHollowRect(g, x, y, WIDTH, height, ColorUtil.withAlpha(Theme.BORDER, (int)(220 * fadeProgress)));
        
        int drawY = y + PADDING;
        hoveredIndex = -1;
        
        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            
            if (item.id.equals("separator")) {
                renderSeparator(g, x + PADDING, drawY, WIDTH - PADDING * 2);
                drawY += 6;
                continue;
            }
            
            boolean hover = mouseX >= x && mouseX <= x + WIDTH && 
                           mouseY >= drawY && mouseY <= drawY + ITEM_HEIGHT;
            if (hover) hoveredIndex = i;
            
            renderMenuItem(g, x + PADDING, drawY, WIDTH - PADDING * 2, item, hover, fadeProgress);
            drawY += ITEM_HEIGHT;
        }
    }
    
    private void renderMenuItem(GuiGraphicsExtractor g, int x, int y, int width, MenuItem item, boolean hover, float fade) {
        if (hover) {
            int hoverColor = ColorUtil.withAlpha(Theme.ACCENT, (int)(100 * fade));
            Panel.draw(g, x, y, width, ITEM_HEIGHT, hoverColor);
        }
        
        if (!item.enabled) {
            int disabledOverlay = ColorUtil.withAlpha(Theme.BACKGROUND, (int)(150 * fade));
            Panel.draw(g, x, y, width, ITEM_HEIGHT, disabledOverlay);
        }
        
        int iconX = x + 8;
        int iconY = y + (ITEM_HEIGHT - 12) / 2;
        int iconColor = item.enabled ? Theme.FOREGROUND : Theme.MUTED_FOREGROUND;
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(item.icon), iconX, iconY, ColorUtil.withAlpha(iconColor, (int)(255 * fade)));
        
        int textX = iconX + 20;
        int textY = y + (ITEM_HEIGHT - net.minecraft.client.Minecraft.getInstance().font.lineHeight) / 2;
        int textColor = item.enabled ? Theme.FOREGROUND : Theme.MUTED_FOREGROUND;
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(item.label), textX, textY, ColorUtil.withAlpha(textColor, (int)(255 * fade)));
    }
    
    private void renderSeparator(GuiGraphicsExtractor g, int x, int y, int width) {
        int lineColor = ColorUtil.withAlpha(Theme.BORDER, 150);
        g.fill(x, y + 3, x + width, y + 3 + 1, lineColor);
    }
    
    private int calculateHeight() {
        int height = PADDING * 2;
        for (MenuItem item : items) {
            if (item.id.equals("separator")) {
                height += 6;
            } else {
                height += ITEM_HEIGHT;
            }
        }
        return height;
    }
    
    public String handleClick(int mouseX, int mouseY) {
        if (!visible) return null;
        
        if (hoveredIndex >= 0 && hoveredIndex < items.size()) {
            MenuItem item = items.get(hoveredIndex);
            if (item.enabled && !item.id.equals("separator")) {
                hide();
                return item.id;
            }
        }
        
        int height = calculateHeight();
        if (mouseX < x || mouseX > x + WIDTH || mouseY < y || mouseY > y + height) {
            hide();
        }
        
        return null;
    }
    
    private static class MenuItem {
        final String id;
        final String label;
        final String icon;
        final boolean enabled;
        
        MenuItem(String id, String label, String icon, boolean enabled) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            this.enabled = enabled;
        }
    }
}
