package com.crest.client.core.hud.editor;

import com.crest.client.core.hud.profile.HudProfile;
import com.crest.client.core.hud.profile.HudProfileManager;
import com.crest.client.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class HudEditorToolbar {
    
    private static final int HEIGHT = 36;
    private static final int BUTTON_HEIGHT = 26;
    private static final int ICON_BUTTON_WIDTH = 32;
    private static final int PADDING = 6;
    
    private final HudEditorHistory history;
    private boolean previewMode = false;
    
    private final Animated expandAnim;
    private final List<Button> buttons = new ArrayList<>();
    
    public HudEditorToolbar(HudEditorHistory history) {
        this.history = history;
        this.expandAnim = new Animated(0f, 12f);
    }
    
    public void render(GuiGraphicsExtractor g, int screenWidth, int mouseX, int mouseY, float delta) {
        boolean hovering = mouseY <= 48;
        expandAnim.set(hovering ? 1f : 0f);
        expandAnim.tick(delta);
        float expand = expandAnim.get();
        
        if (expand < 0.02f && !hovering) {
            renderPill(g, mouseX, mouseY, delta);
            buttons.clear();
            return;
        }
        
        int offset = (int)((1f - expand) * (HEIGHT + 8));
        int y = 2 - offset;
        float alpha = expand;
        
        int bgColor = ColorUtil.withAlpha(Theme.BACKGROUND, (int)(245 * alpha));
        Panel.draw(g, PADDING, y, screenWidth - PADDING * 2, HEIGHT, bgColor);
        Panel.drawHollowRect(g, PADDING, y, screenWidth - PADDING * 2, HEIGHT, ColorUtil.withAlpha(Theme.BORDER, (int)(200 * alpha)));
        
        buttons.clear();
        if (expand < 0.55f) return;
        
        int x = PADDING + 8;
        
        x = renderProfileSelector(g, x, y + (HEIGHT - BUTTON_HEIGHT) / 2, mouseX, mouseY, delta);
        x += 12;
        
        x = renderDivider(g, x, y + 6, HEIGHT - 12);
        x += 12;
        
        x = renderUndoRedoButtons(g, x, y + (HEIGHT - BUTTON_HEIGHT) / 2, mouseX, mouseY);
        x += 12;
        
        x = renderDivider(g, x, y + 6, HEIGHT - 12);
        x += 12;
        
        int rightX = screenWidth - PADDING - 8;
        rightX = renderCloseButton(g, rightX - ICON_BUTTON_WIDTH, y + (HEIGHT - BUTTON_HEIGHT) / 2, mouseX, mouseY);
        rightX -= 8;
        
        rightX = renderSaveButton(g, rightX - 60, y + (HEIGHT - BUTTON_HEIGHT) / 2, mouseX, mouseY);
    }
    
    /** Compact hint chip shown while the toolbar is collapsed. */
    private void renderPill(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        String label = "\u2699 HUD Editor";
        int pillH = 22;
        int pillW = net.minecraft.client.Minecraft.getInstance().font.width(label) + 28;
        int x = PADDING + 4;
        int y = 4;
        
        boolean hover = mouseX >= x && mouseX <= x + pillW && mouseY >= y && mouseY <= y + pillH;
        Panel.draw(g, x, y, pillW, pillH, ColorUtil.withAlpha(Theme.BACKGROUND, 205));
        Panel.drawHollowRect(g, x, y, pillW, pillH, ColorUtil.withAlpha(hover ? Theme.getAnimatedAccent() : Theme.BORDER, 200));
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(label), x + 14, y + 6, Theme.MUTED_FOREGROUND);
    }
    
    private int renderProfileSelector(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY, float delta) {
        HudProfile current = HudProfileManager.getInstance().getCurrentProfile();
        String profileName = current != null ? current.getName() : "No Profile";
        
        int width = Math.max(120, net.minecraft.client.Minecraft.getInstance().font.width(profileName) + 40);
        boolean hover = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + BUTTON_HEIGHT;
        
        int bgColor = hover ? ColorUtil.withAlpha(Theme.CARD, 250) : ColorUtil.withAlpha(Theme.CARD, 220);
        Panel.draw(g, x, y, width, BUTTON_HEIGHT, bgColor);
        Panel.drawHollowRect(g, x, y, width, BUTTON_HEIGHT, ColorUtil.withAlpha(Theme.BORDER, hover ? 200 : 150));
        
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(profileName), x + 8, y + (BUTTON_HEIGHT - net.minecraft.client.Minecraft.getInstance().font.lineHeight) / 2, Theme.FOREGROUND);
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("▼"), x + width - 20, y + (BUTTON_HEIGHT - net.minecraft.client.Minecraft.getInstance().font.lineHeight) / 2, Theme.MUTED_FOREGROUND);
        
        buttons.add(new Button("profile", x, y, width, BUTTON_HEIGHT));
        return x + width;
    }
    
    private int renderUndoRedoButtons(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        boolean canUndo = history.canUndo();
        boolean undoHover = mouseX >= x && mouseX <= x + ICON_BUTTON_WIDTH && mouseY >= y && mouseY <= y + BUTTON_HEIGHT;
        renderIconButton(g, x, y, "↶", canUndo, undoHover, "undo");
        x += ICON_BUTTON_WIDTH + 4;
        
        boolean canRedo = history.canRedo();
        boolean redoHover = mouseX >= x && mouseX <= x + ICON_BUTTON_WIDTH && mouseY >= y && mouseY <= y + BUTTON_HEIGHT;
        renderIconButton(g, x, y, "↷", canRedo, redoHover, "redo");
        
        return x + ICON_BUTTON_WIDTH;
    }
    
    private int renderSaveButton(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + 60 && mouseY >= y && mouseY <= y + BUTTON_HEIGHT;
        
        int bgColor = hover ? ColorUtil.withAlpha(Theme.ACCENT, 220) : ColorUtil.withAlpha(Theme.ACCENT, 180);
        Panel.draw(g, x, y, 60, BUTTON_HEIGHT, bgColor);
        
        if (hover) {
            Panel.draw(g, x - 2, y - 2, 64, BUTTON_HEIGHT + 4, ColorUtil.withAlpha(Theme.ACCENT, 240));
        }
        
        String text = "✓ Save";
        int textWidth = net.minecraft.client.Minecraft.getInstance().font.width(text);
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(text), x + (60 - textWidth) / 2, y + (BUTTON_HEIGHT - net.minecraft.client.Minecraft.getInstance().font.lineHeight) / 2, 0xFFFFFFFF);
        
        buttons.add(new Button("save", x, y, 60, BUTTON_HEIGHT));
        return x;
    }
    
    private int renderCloseButton(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + ICON_BUTTON_WIDTH && mouseY >= y && mouseY <= y + BUTTON_HEIGHT;
        
        int bgColor = hover ? ColorUtil.withAlpha(0xFF0000, 200) : ColorUtil.withAlpha(Theme.CARD, 220);
        Panel.draw(g, x, y, ICON_BUTTON_WIDTH, BUTTON_HEIGHT, bgColor);
        Panel.drawHollowRect(g, x, y, ICON_BUTTON_WIDTH, BUTTON_HEIGHT, ColorUtil.withAlpha(hover ? 0xFF0000 : Theme.BORDER, 200));
        
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("✗"), x + (ICON_BUTTON_WIDTH - 8) / 2, y + (BUTTON_HEIGHT - net.minecraft.client.Minecraft.getInstance().font.lineHeight) / 2, hover ? 0xFFFFFFFF : Theme.FOREGROUND);
        
        buttons.add(new Button("close", x, y, ICON_BUTTON_WIDTH, BUTTON_HEIGHT));
        return x;
    }
    
    private void renderIconButton(GuiGraphicsExtractor g, int x, int y, String icon, boolean enabled, boolean hover, String id) {
        int bgColor;
        if (!enabled) {
            bgColor = ColorUtil.withAlpha(Theme.CARD, 150);
        } else if (hover) {
            bgColor = ColorUtil.withAlpha(Theme.CARD, 250);
        } else {
            bgColor = ColorUtil.withAlpha(Theme.CARD, 220);
        }
        
        Panel.draw(g, x, y, ICON_BUTTON_WIDTH, BUTTON_HEIGHT, bgColor);
        Panel.drawHollowRect(g, x, y, ICON_BUTTON_WIDTH, BUTTON_HEIGHT, ColorUtil.withAlpha(Theme.BORDER, enabled && hover ? 200 : 150));
        
        int textColor = enabled ? (hover ? Theme.FOREGROUND : Theme.MUTED_FOREGROUND) : ColorUtil.withAlpha(Theme.MUTED_FOREGROUND, 100);
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(icon), x + (ICON_BUTTON_WIDTH - net.minecraft.client.Minecraft.getInstance().font.width(icon)) / 2, y + (BUTTON_HEIGHT - net.minecraft.client.Minecraft.getInstance().font.lineHeight) / 2, textColor);
        
        if (enabled) {
            buttons.add(new Button(id, x, y, ICON_BUTTON_WIDTH, BUTTON_HEIGHT));
        }
    }
    
    private int renderDivider(GuiGraphicsExtractor g, int x, int y, int height) {
        g.fill(x, y, x + 1, y + height, ColorUtil.withAlpha(Theme.BORDER, 150));
        return x;
    }
    
    public String handleClick(int mouseX, int mouseY) {
        for (Button button : buttons) {
            if (button.contains(mouseX, mouseY)) {
                return button.id;
            }
        }
        return null;
    }
    
    public void handleAction(String action) {
        switch (action) {
            case "undo" -> history.undo();
            case "redo" -> history.redo();
        }
    }
    
    public void setPreviewMode(boolean active) {
        this.previewMode = active;
    }
    
    public boolean isPreviewMode() {
        return previewMode;
    }
    
    public boolean isGridEnabled() {
        return true;
    }
    
    public boolean isSnapEnabled() {
        return true;
    }
    
    public int getHeight() {
        return HEIGHT + 4;
    }
    
    private static class Button {
        final String id;
        final int x, y, width, height;
        
        Button(String id, int x, int y, int width, int height) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
        
        boolean contains(int mx, int my) {
            return mx >= x && mx <= x + width && my >= y && my <= y + height;
        }
    }
}
