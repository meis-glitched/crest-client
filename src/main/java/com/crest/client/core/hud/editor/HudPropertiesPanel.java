package com.crest.client.core.hud.editor;

import com.crest.client.core.HudModule;
import com.crest.client.core.hud.profile.HudProfile;
import com.crest.client.core.hud.widgets.AnchorSelector;
import com.crest.client.core.hud.widgets.PropertySlider;
import com.crest.client.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class HudPropertiesPanel {
    
    private static final int WIDTH = 220;
    private static final int PADDING = 12;
    
    private HudModule selectedModule;
    private HudProfile.ModuleSettings settings;
    
    private PropertySlider scaleSlider;
    private PropertySlider opacitySlider;
    private PropertySlider bgOpacitySlider;
    private PropertySlider borderThicknessSlider;
    private PropertySlider shadowBlurSlider;
    private PropertySlider fontSizeSlider;
    private AnchorSelector anchorSelector;
    
    private final Animated slideAnim;
    private boolean visible = true;
    
    private int scrollOffset = 0;
    private int contentHeight = 0;
    
    public HudPropertiesPanel() {
        this.slideAnim = new Animated(1f, 12f);
        initializeControls();
    }
    
    private void initializeControls() {
        scaleSlider = new PropertySlider("Scale", 100f, 50f, 200f, "%");
        opacitySlider = new PropertySlider("Opacity", 100f, 0f, 100f, "%");
        bgOpacitySlider = new PropertySlider("Background Opacity", 80f, 0f, 100f, "%");
        borderThicknessSlider = new PropertySlider("Border Thickness", 1f, 0f, 5f, "px");
        shadowBlurSlider = new PropertySlider("Shadow Blur", 2f, 0f, 10f, "px");
        fontSizeSlider = new PropertySlider("Font Size", 12f, 8f, 24f, "px");
        anchorSelector = new AnchorSelector(4);
    }
    
    public void setSelectedModule(HudModule module, HudProfile.ModuleSettings settings) {
        this.selectedModule = module;
        this.settings = settings;
        
        if (settings != null) {
            scaleSlider.setValue(settings.scale * 100f);
            opacitySlider.setValue(settings.opacity * 100f);
            bgOpacitySlider.setValue(settings.backgroundOpacity * 100f);
            borderThicknessSlider.setValue(settings.borderThickness);
            shadowBlurSlider.setValue(settings.shadowBlur);
            fontSizeSlider.setValue(settings.fontSize);
            anchorSelector.setSelectedAnchor(settings.anchor);
        }
    }
    
    public void render(GuiGraphicsExtractor g, int screenWidth, int screenHeight, int mouseX, int mouseY, float delta) {
        slideAnim.set(visible ? 1f : 0f); slideAnim.tick(delta);
        float slideProgress = slideAnim.get();
        
        if (slideProgress < 0.01f) return;
        
        int x = screenWidth - (int)(WIDTH * slideProgress);
        int y = 40;
        int height = screenHeight - y - 10;
        
        Panel.draw(g, x, y, WIDTH, height, ColorUtil.withAlpha(Theme.BACKGROUND, 240));
        Panel.drawHollowRect(g, x, y, WIDTH, height, ColorUtil.withAlpha(Theme.BORDER, 200));
        
        if (selectedModule == null) {
            renderEmptyState(g, x, y, WIDTH, height);
            return;
        }
        
        int contentX = x + PADDING;
        int contentWidth = WIDTH - PADDING * 2;
        int drawY = y + PADDING - scrollOffset;
        
        
        g.enableScissor(x, y, x + WIDTH, y + height);
        
        drawY = renderHeader(g, contentX, drawY, contentWidth);
        drawY += 16;
        
        drawY = renderPositionSection(g, contentX, drawY, contentWidth);
        drawY += 16;
        
        drawY = renderAnchorSection(g, contentX, drawY, contentWidth, mouseX, mouseY, delta);
        drawY += 16;
        
        drawY = renderAppearanceSection(g, contentX, drawY, contentWidth, mouseX, mouseY, delta);
        drawY += 16;
        
        drawY = renderBackgroundSection(g, contentX, drawY, contentWidth, mouseX, mouseY, delta);
        drawY += 16;
        
        drawY = renderBorderSection(g, contentX, drawY, contentWidth, mouseX, mouseY, delta);
        drawY += 16;
        
        drawY = renderShadowSection(g, contentX, drawY, contentWidth, mouseX, mouseY, delta);
        
        contentHeight = drawY - (y + PADDING) + PADDING;
        
        g.disableScissor();
        
        
        renderActionButtons(g, x + PADDING, y + height - 40, contentWidth);
    }
    
    private void renderEmptyState(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        String text = "Select a module to";
        String text2 = "edit its properties";
        int textWidth = net.minecraft.client.Minecraft.getInstance().font.width(text);
        int textWidth2 = net.minecraft.client.Minecraft.getInstance().font.width(text2);
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(text), x + (width - textWidth) / 2, y + height / 2 - 10, Theme.MUTED_FOREGROUND);
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(text2), x + (width - textWidth2) / 2, y + height / 2 + 2, Theme.MUTED_FOREGROUND);
    }
    
    private int renderHeader(GuiGraphicsExtractor g, int x, int y, int width) {
        String icon = getModuleIcon(selectedModule.getId());
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(icon + " " + selectedModule.getName()), x, y, Theme.FOREGROUND);
        return y + net.minecraft.client.Minecraft.getInstance().font.lineHeight + 8;
    }
    
    private int renderPositionSection(GuiGraphicsExtractor g, int x, int y, int width) {
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("Position"), x, y, Theme.FOREGROUND);
        y += net.minecraft.client.Minecraft.getInstance().font.lineHeight + 8;
        
        int inputWidth = (width - 8) / 2;
        
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("X:"), x, y, Theme.MUTED_FOREGROUND);
        String xText = String.valueOf(settings.x);
        Panel.draw(g, x + 20, y - 2, inputWidth - 20, 16, ColorUtil.withAlpha(Theme.CARD, 200));
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(xText), x + 24, y, Theme.FOREGROUND);
        
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("Y:"), x + inputWidth + 8, y, Theme.MUTED_FOREGROUND);
        String yText = String.valueOf(settings.y);
        Panel.draw(g, x + inputWidth + 28, y - 2, inputWidth - 20, 16, ColorUtil.withAlpha(Theme.CARD, 200));
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(yText), x + inputWidth + 32, y, Theme.FOREGROUND);
        
        return y + 20;
    }
    
    private int renderAnchorSection(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float delta) {
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("Anchor"), x, y, Theme.FOREGROUND);
        y += net.minecraft.client.Minecraft.getInstance().font.lineHeight + 8;
        
        int anchorX = x + (width - anchorSelector.getWidth()) / 2;
        anchorSelector.render(g, anchorX, y, mouseX, mouseY, delta);
        
        return y + anchorSelector.getHeight() + 8;
    }
    
    private int renderAppearanceSection(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float delta) {
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("Appearance"), x, y, Theme.FOREGROUND);
        y += net.minecraft.client.Minecraft.getInstance().font.lineHeight + 8;
        
        boolean scaleHover = mouseY >= y && mouseY <= y + 24 && mouseX >= x && mouseX <= x + width;
        scaleSlider.render(g, x, y, width, 24, scaleHover, delta);
        y += 32;
        
        boolean opacityHover = mouseY >= y && mouseY <= y + 24 && mouseX >= x && mouseX <= x + width;
        opacitySlider.render(g, x, y, width, 24, opacityHover, delta);
        y += 32;
        
        boolean fontHover = mouseY >= y && mouseY <= y + 24 && mouseX >= x && mouseX <= x + width;
        fontSizeSlider.render(g, x, y, width, 24, fontHover, delta);
        y += 32;
        
        return y;
    }
    
    private int renderBackgroundSection(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float delta) {
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("Background"), x, y, Theme.FOREGROUND);
        y += net.minecraft.client.Minecraft.getInstance().font.lineHeight + 8;
        
        boolean checkboxHover = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 16;
        renderCheckbox(g, x, y, "Enabled", settings.backgroundEnabled, checkboxHover);
        y += 24;
        
        if (settings.backgroundEnabled) {
            boolean bgOpacityHover = mouseY >= y && mouseY <= y + 24 && mouseX >= x && mouseX <= x + width;
            bgOpacitySlider.render(g, x, y, width, 24, bgOpacityHover, delta);
            y += 32;
        }
        
        return y;
    }
    
    private int renderBorderSection(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float delta) {
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("Border"), x, y, Theme.FOREGROUND);
        y += net.minecraft.client.Minecraft.getInstance().font.lineHeight + 8;
        
        boolean checkboxHover = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 16;
        renderCheckbox(g, x, y, "Enabled", settings.borderEnabled, checkboxHover);
        y += 24;
        
        if (settings.borderEnabled) {
            boolean thicknessHover = mouseY >= y && mouseY <= y + 24 && mouseX >= x && mouseX <= x + width;
            borderThicknessSlider.render(g, x, y, width, 24, thicknessHover, delta);
            y += 32;
        }
        
        return y;
    }
    
    private int renderShadowSection(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float delta) {
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("Shadow"), x, y, Theme.FOREGROUND);
        y += net.minecraft.client.Minecraft.getInstance().font.lineHeight + 8;
        
        boolean checkboxHover = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 16;
        renderCheckbox(g, x, y, "Enabled", settings.shadowEnabled, checkboxHover);
        y += 24;
        
        if (settings.shadowEnabled) {
            boolean blurHover = mouseY >= y && mouseY <= y + 24 && mouseX >= x && mouseX <= x + width;
            shadowBlurSlider.render(g, x, y, width, 24, blurHover, delta);
            y += 32;
        }
        
        return y;
    }
    
    private void renderCheckbox(GuiGraphicsExtractor g, int x, int y, String label, boolean checked, boolean hover) {
        int checkboxSize = 16;
        int bgColor = hover ? ColorUtil.withAlpha(Theme.CARD, 250) : ColorUtil.withAlpha(Theme.CARD, 200);
        Panel.draw(g, x, y, checkboxSize, checkboxSize, bgColor);
        
        int borderColor = checked ? Theme.ACCENT : Theme.BORDER;
        Panel.drawHollowRect(g, x, y, checkboxSize, checkboxSize, ColorUtil.withAlpha(borderColor, 200));
        
        if (checked) {
            g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("✓"), x + 3, y + 3, Theme.ACCENT);
        }
        
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(label), x + checkboxSize + 8, y + 4, Theme.FOREGROUND);
    }
    
    private void renderActionButtons(GuiGraphicsExtractor g, int x, int y, int width) {
        int buttonWidth = (width - 8) / 2;
        int buttonHeight = 24;
        
        Panel.draw(g, x, y, buttonWidth, buttonHeight, ColorUtil.withAlpha(Theme.ACCENT, 240));
        String applyText = "Apply";
        int applyWidth = net.minecraft.client.Minecraft.getInstance().font.width(applyText);
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(applyText), x + (buttonWidth - applyWidth) / 2, y + (buttonHeight - net.minecraft.client.Minecraft.getInstance().font.lineHeight) / 2, 0xFFFFFFFF);
        
        Panel.draw(g, x + buttonWidth + 8, y, buttonWidth, buttonHeight, ColorUtil.withAlpha(Theme.CARD, 200));
        Panel.drawHollowRect(g, x + buttonWidth + 8, y, buttonWidth, buttonHeight, ColorUtil.withAlpha(Theme.BORDER, 200));
        String resetText = "Reset";
        int resetWidth = net.minecraft.client.Minecraft.getInstance().font.width(resetText);
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(resetText), x + buttonWidth + 8 + (buttonWidth - resetWidth) / 2, y + (buttonHeight - net.minecraft.client.Minecraft.getInstance().font.lineHeight) / 2, Theme.FOREGROUND);
    }
    
    public boolean handleClick(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!visible || selectedModule == null) return false;
        
        int x = screenWidth - WIDTH;
        int y = 40;
        int height = screenHeight - y - 10;
        
        if (mouseX < x || mouseX > x + WIDTH || mouseY < y || mouseY > y + height) {
            return false;
        }
        
        int contentX = x + PADDING;
        int contentWidth = WIDTH - PADDING * 2;
        
        if (anchorSelector.handleClick(mouseX, mouseY, contentX + (contentWidth - anchorSelector.getWidth()) / 2, y + 100 - scrollOffset)) {
            settings.anchor = anchorSelector.getSelectedAnchor();
            return true;
        }
        
        return true;
    }
    
    public boolean handleDrag(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (!visible || selectedModule == null) return false;
        
        int x = screenWidth - WIDTH;
        int contentX = x + PADDING;
        int contentWidth = WIDTH - PADDING * 2;
        
        scaleSlider.handleMouseDrag(mouseX, mouseY, contentX, 0, contentWidth);
        opacitySlider.handleMouseDrag(mouseX, mouseY, contentX, 0, contentWidth);
        bgOpacitySlider.handleMouseDrag(mouseX, mouseY, contentX, 0, contentWidth);
        borderThicknessSlider.handleMouseDrag(mouseX, mouseY, contentX, 0, contentWidth);
        shadowBlurSlider.handleMouseDrag(mouseX, mouseY, contentX, 0, contentWidth);
        fontSizeSlider.handleMouseDrag(mouseX, mouseY, contentX, 0, contentWidth);
        
        if (scaleSlider.isDragging()) settings.scale = scaleSlider.getValue() / 100f;
        if (opacitySlider.isDragging()) settings.opacity = opacitySlider.getValue() / 100f;
        if (bgOpacitySlider.isDragging()) settings.backgroundOpacity = bgOpacitySlider.getValue() / 100f;
        if (borderThicknessSlider.isDragging()) settings.borderThickness = borderThicknessSlider.getValue();
        if (shadowBlurSlider.isDragging()) settings.shadowBlur = shadowBlurSlider.getValue();
        if (fontSizeSlider.isDragging()) settings.fontSize = (int)fontSizeSlider.getValue();
        
        return scaleSlider.isDragging() || opacitySlider.isDragging() || bgOpacitySlider.isDragging() ||
               borderThicknessSlider.isDragging() || shadowBlurSlider.isDragging() || fontSizeSlider.isDragging();
    }
    
    public void handleMouseUp() {
        scaleSlider.handleMouseUp();
        opacitySlider.handleMouseUp();
        bgOpacitySlider.handleMouseUp();
        borderThicknessSlider.handleMouseUp();
        shadowBlurSlider.handleMouseUp();
        fontSizeSlider.handleMouseUp();
    }
    
    private String getModuleIcon(String id) {
        String lower = id.toLowerCase();
        if (lower.contains("cps")) return "⚔️";
        if (lower.contains("armor")) return "🛡️";
        if (lower.contains("zoom")) return "🔍";
        if (lower.contains("fps")) return "📊";
        return "📦";
    }
    
    public void toggle() {
        visible = !visible;
    }
    
    public boolean isVisible() {
        return visible && slideAnim.get() > 0.01f;
    }
    
    public int getWidth() {
        return (int)(WIDTH * slideAnim.get());
    }
}
