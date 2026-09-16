package com.crest.client.core.hud.editor;

import com.crest.client.core.CrestModules;
import com.crest.client.core.HudModule;
import com.crest.client.core.HudSettings;
import com.crest.client.core.hud.profile.HudProfile;
import com.crest.client.core.hud.profile.HudProfileManager;
import com.crest.client.core.hud.widgets.AnchorSelector;
import com.crest.client.core.hud.widgets.PropertySlider;
import com.crest.client.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class HudPropertiesPanel {

    private static final int WIDTH = 220;
    private static final int PADDING = 12;
    private static final int PICKER_HEIGHT = 176;

    private HudModule selectedModule;
    private HudProfile.ModuleSettings settings;
    private List<HudModule> selectedModules = new ArrayList<>();

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

    private float lastAppliedScale = Float.NaN;

    private int scaleY, opacityY, fontSizeY;
    private int bgCheckY, bgOpacityY, bgColorY;
    private int borderCheckY, borderThickY, borderColorY;
    private int shadowCheckY, shadowBlurY, shadowColorY;
    private int actionY;
    private int anchorY;
    private int contentX, contentWidth;

    private String openPicker = null;
    private final ColorPicker colorPicker = new ColorPicker();
    private int pickerX, pickerY, pickerW, pickerH;
    private final int[] swatchSize = {18, 14};

    private Runnable previewToggleHandler;
    private boolean previewActive;
    private int previewY;

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
        setSelectedModule(module, settings, module != null ? List.of(module) : List.of());
    }

    public void setSelectedModule(HudModule module, HudProfile.ModuleSettings settings, List<HudModule> group) {
        this.selectedModule = module;
        this.settings = settings;
        this.selectedModules = group != null ? new ArrayList<>(group) : new ArrayList<>();

        if (settings != null) {
            scaleSlider.setValue(settings.scale * 100f);
            opacitySlider.setValue(settings.opacity * 100f);
            bgOpacitySlider.setValue(settings.backgroundOpacity * 100f);
            borderThicknessSlider.setValue(settings.borderThickness);
            shadowBlurSlider.setValue(settings.shadowBlur);
            fontSizeSlider.setValue(settings.fontSize);
            anchorSelector.setSelectedAnchor(settings.anchor);
        }
        lastAppliedScale = settings != null ? settings.scale : Float.NaN;
        openPicker = null;
        scrollOffset = 0;
    }

    public void render(GuiGraphicsExtractor g, int screenWidth, int screenHeight, int mouseX, int mouseY, float delta) {
        slideAnim.set(visible ? 1f : 0f); slideAnim.tick(delta);
        float slideProgress = slideAnim.get();

        if (slideProgress < 0.01f) return;

        int x = screenWidth - (int)(WIDTH * slideProgress);
        int y = 40;
        int height = screenHeight - y - 10;

        if (selectedModule == null) {
            renderEmptyState(g, x, y, WIDTH, height);
            return;
        }

        g.fill(x, y, x + WIDTH, y + height, 0xFF20202C);
        Panel.drawHollowRect(g, x, y, WIDTH, height, ColorUtil.withAlpha(Theme.BORDER, 230));
        g.fill(x, y, x + 3, y + height, 0xFF3553F0);

        int sectionX = x + PADDING;
        int sectionWidth = WIDTH - PADDING * 2;
        int drawY = y + PADDING - scrollOffset;

        g.enableScissor(x, y, x + WIDTH, y + height);

        drawY = renderHeader(g, sectionX, drawY, sectionWidth);
        drawY += 16;

        drawY = renderPositionSection(g, sectionX, drawY, sectionWidth);
        drawY += 16;

        drawY = renderAnchorSection(g, sectionX, drawY, sectionWidth, mouseX, mouseY, delta);
        drawY += 16;

        drawY = renderAppearanceSection(g, sectionX, drawY, sectionWidth, mouseX, mouseY, delta);
        drawY += 16;

        drawY = renderBackgroundSection(g, sectionX, drawY + 12, sectionWidth, mouseX, mouseY, delta);
        drawY += 16;

        drawY = renderBorderSection(g, sectionX, drawY + 12, sectionWidth, mouseX, mouseY, delta);
        drawY += 16;

        drawY = renderShadowSection(g, sectionX, drawY + 12, sectionWidth, mouseX, mouseY, delta);

        contentHeight = drawY - (y + PADDING) + PADDING;

        g.disableScissor();

        int buttonY = y + height - 40;
        actionY = buttonY;
        renderActionButtons(g, x + PADDING, buttonY, sectionWidth);

        previewY = buttonY - 32;
        renderPreviewButton(g, x + PADDING, previewY, sectionWidth, mouseX, mouseY);
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

        if (selectedModules.size() > 1) {
            String count = selectedModules.size() + " selected";
            int cw = net.minecraft.client.Minecraft.getInstance().font.width(count);
            g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(count), x + width - cw, y, Theme.ACCENT);
        }
        return y + net.minecraft.client.Minecraft.getInstance().font.lineHeight + 8;
    }

    private int renderPositionSection(GuiGraphicsExtractor g, int x, int y, int width) {
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("Position"), x, y, Theme.FOREGROUND);
        y += net.minecraft.client.Minecraft.getInstance().font.lineHeight + 8;

        int inputWidth = (width - 8) / 2;

        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("X:"), x, y, Theme.MUTED_FOREGROUND);
        String xText = String.valueOf(selectedModule.getX());
        Panel.draw(g, x + 20, y - 2, inputWidth - 20, 16, ColorUtil.withAlpha(Theme.CARD, 200));
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(xText), x + 24, y, Theme.FOREGROUND);

        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("Y:"), x + inputWidth + 8, y, Theme.MUTED_FOREGROUND);
        String yText = String.valueOf(selectedModule.getY());
        Panel.draw(g, x + inputWidth + 28, y - 2, inputWidth - 20, 16, ColorUtil.withAlpha(Theme.CARD, 200));
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(yText), x + inputWidth + 32, y, Theme.FOREGROUND);

        return y + 20;
    }

    private int renderAnchorSection(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float delta) {
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("Anchor"), x, y, Theme.FOREGROUND);
        y += net.minecraft.client.Minecraft.getInstance().font.lineHeight + 8;

        int anchorX = x + (width - anchorSelector.getWidth()) / 2;
        anchorSelector.render(g, anchorX, y, mouseX, mouseY, delta);
        anchorY = y;

        return y + anchorSelector.getHeight() + 8;
    }

    private int renderAppearanceSection(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float delta) {
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("Appearance"), x, y, Theme.FOREGROUND);
        y += net.minecraft.client.Minecraft.getInstance().font.lineHeight + 8;

        if (selectedModule.supportsScale()) {
            boolean scaleHover = mouseY >= y && mouseY <= y + 24 && mouseX >= x && mouseX <= x + width;
            scaleSlider.render(g, x, y, width, 24, scaleHover, delta);
            scaleY = y;
            y += 32;
        }

        if (selectedModule.supportsOpacity()) {
            boolean opacityHover = mouseY >= y && mouseY <= y + 24 && mouseX >= x && mouseX <= x + width;
            opacitySlider.render(g, x, y, width, 24, opacityHover, delta);
            opacityY = y;
            y += 32;
        }

        boolean fontHover = mouseY >= y && mouseY <= y + 24 && mouseX >= x && mouseX <= x + width;
        fontSizeSlider.render(g, x, y, width, 24, fontHover, delta);
        fontSizeY = y;
        y += 32;

        return y;
    }

    private int renderBackgroundSection(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float delta) {
        if (!selectedModule.supportsChrome()) return y;
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("Background"), x, y, Theme.FOREGROUND);
        y += net.minecraft.client.Minecraft.getInstance().font.lineHeight + 8;

        boolean checkboxHover = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 16;
        renderCheckbox(g, x, y, "Enabled", settings.backgroundEnabled, checkboxHover);
        bgCheckY = y;
        y += 24;

        if (settings.backgroundEnabled) {
            renderColorRow(g, x, y, "Background Color", settings.backgroundColor,
                    "bg".equals(openPicker), mouseX, mouseY);
            bgColorY = y;
            y += 22;

            boolean bgOpacityHover = mouseY >= y && mouseY <= y + 24 && mouseX >= x && mouseX <= x + width;
            bgOpacitySlider.render(g, x, y, width, 24, bgOpacityHover, delta);
            bgOpacityY = y;
            y += 32;
        }

        if ("bg".equals(openPicker)) {
            y = renderPicker(g, x, y, width, mouseX, mouseY, delta, "bg");
        }

        return y;
    }

    private int renderBorderSection(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float delta) {
        if (!selectedModule.supportsChrome()) return y;
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("Border"), x, y, Theme.FOREGROUND);
        y += net.minecraft.client.Minecraft.getInstance().font.lineHeight + 8;

        boolean checkboxHover = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 16;
        renderCheckbox(g, x, y, "Enabled", settings.borderEnabled, checkboxHover);
        borderCheckY = y;
        y += 24;

        if (settings.borderEnabled) {
            renderColorRow(g, x, y, "Border Color", settings.borderColor,
                    "border".equals(openPicker), mouseX, mouseY);
            borderColorY = y;
            y += 22;

            boolean thicknessHover = mouseY >= y && mouseY <= y + 24 && mouseX >= x && mouseX <= x + width;
            borderThicknessSlider.render(g, x, y, width, 24, thicknessHover, delta);
            borderThickY = y;
            y += 32;
        }

        if ("border".equals(openPicker)) {
            y = renderPicker(g, x, y, width, mouseX, mouseY, delta, "border");
        }

        return y;
    }

    private int renderShadowSection(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float delta) {
        if (!selectedModule.supportsChrome()) return y;
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("Shadow"), x, y, Theme.FOREGROUND);
        y += net.minecraft.client.Minecraft.getInstance().font.lineHeight + 8;

        boolean checkboxHover = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 16;
        renderCheckbox(g, x, y, "Enabled", settings.shadowEnabled, checkboxHover);
        shadowCheckY = y;
        y += 24;

        if (settings.shadowEnabled) {
            renderColorRow(g, x, y, "Shadow Color", settings.shadowColor,
                    "shadow".equals(openPicker), mouseX, mouseY);
            shadowColorY = y;
            y += 22;

            boolean blurHover = mouseY >= y && mouseY <= y + 24 && mouseX >= x && mouseX <= x + width;
            shadowBlurSlider.render(g, x, y, width, 24, blurHover, delta);
            shadowBlurY = y;
            y += 32;
        }

        if ("shadow".equals(openPicker)) {
            y = renderPicker(g, x, y, width, mouseX, mouseY, delta, "shadow");
        }

        return y;
    }

    private void renderColorRow(GuiGraphicsExtractor g, int x, int y, String label, int color, boolean open, int mouseX, int mouseY) {
        int swX = x;
        int swY = y - 1;
        boolean hover = mouseX >= swX && mouseX <= swX + swatchSize[0] && mouseY >= swY && mouseY <= swY + swatchSize[1];

        int colorAlpha = ColorUtil.getA(color);
        Panel.draw(g, swX, swY, swatchSize[0], swatchSize[1],
                colorAlpha == 0 ? Theme.CARD : ColorUtil.withAlpha(color, 255));
        Panel.drawHollowRect(g, swX, swY, swatchSize[0], swatchSize[1],
                ColorUtil.withAlpha(open ? Theme.getAnimatedAccent() : (hover ? Theme.BORDER : Theme.BORDER_LIGHT), 230));

        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(label), swX + swatchSize[0] + 8, y, Theme.FOREGROUND);
    }

    private int renderPicker(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float delta, String key) {
        colorPicker.layout(x, y, width, PICKER_HEIGHT);
        pickerX = x;
        pickerY = y;
        pickerW = width;
        pickerH = PICKER_HEIGHT;
        colorPicker.render(g, net.minecraft.client.Minecraft.getInstance().font, mouseX, mouseY, 255);
        return y + PICKER_HEIGHT + 6;
    }

    private void renderCheckbox(GuiGraphicsExtractor g, int x, int y, String label, boolean checked, boolean hover) {
        int checkboxSize = 16;
        int bgColor = hover ? ColorUtil.withAlpha(Theme.CARD, 250) : ColorUtil.withAlpha(Theme.CARD, 200);
        Panel.draw(g, x, y, checkboxSize, checkboxSize, bgColor);

        int borderColor = checked ? Theme.ACCENT : Theme.BORDER;
        Panel.drawHollowRect(g, x, y, checkboxSize, checkboxSize, ColorUtil.withAlpha(borderColor, 200));

        if (checked) {
            g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("\u2713"), x + 3, y + 3, Theme.ACCENT);
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

    public void setPreviewToggleHandler(Runnable handler) {
        this.previewToggleHandler = handler;
    }

    public void setPreviewActive(boolean active) {
        this.previewActive = active;
    }

    private void renderPreviewButton(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY) {
        int buttonHeight = 24;
        boolean hover = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + buttonHeight;

        int bgColor = previewActive
                ? (hover ? ColorUtil.withAlpha(Theme.ACCENT, 240) : ColorUtil.withAlpha(Theme.ACCENT, 200))
                : (hover ? ColorUtil.withAlpha(Theme.CARD, 250) : ColorUtil.withAlpha(Theme.CARD, 200));
        Panel.draw(g, x, y, width, buttonHeight, bgColor);

        int borderColor = previewActive ? Theme.ACCENT : Theme.BORDER;
        Panel.drawHollowRect(g, x, y, width, buttonHeight, ColorUtil.withAlpha(borderColor, hover ? 220 : 180));

        String text = previewActive ? "\u2713 Preview Mode" : "\uD83D\uDC41 Preview Mode";
        int textWidth = net.minecraft.client.Minecraft.getInstance().font.width(text);
        int textColor = previewActive ? 0xFFFFFFFF : Theme.FOREGROUND;
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(text), x + (width - textWidth) / 2, y + (buttonHeight - net.minecraft.client.Minecraft.getInstance().font.lineHeight) / 2, textColor);
    }

    public boolean handleClick(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!visible || selectedModule == null) return false;

        int x = screenWidth - WIDTH;
        int y = 40;
        int height = screenHeight - y - 10;

        if (mouseX < x || mouseX > x + WIDTH || mouseY < y || mouseY > y + height) {
            return false;
        }

        int sectionX = x + PADDING;
        int sectionWidth = WIDTH - PADDING * 2;

        if (openPicker != null && mouseX >= pickerX && mouseX <= pickerX + pickerW &&
                mouseY >= pickerY && mouseY <= pickerY + pickerH) {
            int doneRight = pickerX + pickerW - Spacing.S2;
            int doneLeft = pickerX + pickerW - Spacing.S4;
            int readoutY = pickerY + PICKER_HEIGHT - Spacing.S4 - 12;
            if (mouseX >= doneLeft && mouseX <= doneRight && mouseY >= readoutY - 2 && mouseY <= readoutY + 12) {
                openPicker = null;
                return true;
            }
            colorPicker.handleClick(mouseX, mouseY);
            return true;
        }

        int anchorY = this.anchorY;
        if (anchorSelector.handleClick(mouseX, mouseY, sectionX + (sectionWidth - anchorSelector.getWidth()) / 2, anchorY)) {
            settings.anchor = anchorSelector.getSelectedAnchor();
            applyAnchor(settings.anchor);
            return true;
        }

        int buttonX = x + PADDING;
        int buttonWidth = (sectionWidth - 8) / 2;
        if (mouseY >= previewY && mouseY <= previewY + 24 && mouseX >= buttonX && mouseX <= x + WIDTH - PADDING && previewToggleHandler != null) {
            previewToggleHandler.run();
            return true;
        }
        if (mouseY >= actionY && mouseY <= actionY + 24) {
            if (mouseX >= buttonX && mouseX <= buttonX + buttonWidth) {
                applySettings();
                return true;
            }
            if (mouseX >= buttonX + buttonWidth + 8 && mouseX <= buttonX + buttonWidth + 8 + buttonWidth) {
                resetSettings();
                return true;
            }
        }

        if (mouseX >= sectionX && mouseX <= x + WIDTH - PADDING) {
            if (settings.backgroundEnabled && mouseY >= bgColorY && mouseY <= bgColorY + swatchSize[1] + 2) {
                togglePicker("bg");
                return true;
            }
            if (settings.borderEnabled && mouseY >= borderColorY && mouseY <= borderColorY + swatchSize[1] + 2) {
                togglePicker("border");
                return true;
            }
            if (settings.shadowEnabled && mouseY >= shadowColorY && mouseY <= shadowColorY + swatchSize[1] + 2) {
                togglePicker("shadow");
                return true;
            }
            if (settings.backgroundEnabled && mouseY >= bgOpacityY && mouseY <= bgOpacityY + 24) {
                bgOpacitySlider.handleMouseDown(mouseX, mouseY, sectionX, bgOpacityY, sectionWidth);
                return true;
            }
            if (settings.borderEnabled && mouseY >= borderThickY && mouseY <= borderThickY + 24) {
                borderThicknessSlider.handleMouseDown(mouseX, mouseY, sectionX, borderThickY, sectionWidth);
                return true;
            }
            if (settings.shadowEnabled && mouseY >= shadowBlurY && mouseY <= shadowBlurY + 24) {
                shadowBlurSlider.handleMouseDown(mouseX, mouseY, sectionX, shadowBlurY, sectionWidth);
                return true;
            }
            if (selectedModule.supportsScale() && mouseY >= scaleY && mouseY <= scaleY + 24) {
                scaleSlider.handleMouseDown(mouseX, mouseY, sectionX, scaleY, sectionWidth);
                return true;
            }
            if (selectedModule.supportsOpacity() && mouseY >= opacityY && mouseY <= opacityY + 24) {
                opacitySlider.handleMouseDown(mouseX, mouseY, sectionX, opacityY, sectionWidth);
                return true;
            }
            if (mouseY >= fontSizeY && mouseY <= fontSizeY + 24) {
                fontSizeSlider.handleMouseDown(mouseX, mouseY, sectionX, fontSizeY, sectionWidth);
                return true;
            }
            if (mouseY >= bgCheckY && mouseY <= bgCheckY + 16) {
                applyToAll(s -> s.backgroundEnabled = !s.backgroundEnabled);
                return true;
            }
            if (mouseY >= borderCheckY && mouseY <= borderCheckY + 16) {
                applyToAll(s -> s.borderEnabled = !s.borderEnabled);
                return true;
            }
            if (mouseY >= shadowCheckY && mouseY <= shadowCheckY + 16) {
                applyToAll(s -> s.shadowEnabled = !s.shadowEnabled);
                return true;
            }
        }

        return true;
    }

    private void togglePicker(String key) {
        if (openPicker != null && openPicker.equals(key)) {
            openPicker = null;
            return;
        }
        openPicker = key;
        int currentColor = switch (key) {
            case "bg" -> settings.backgroundColor;
            case "border" -> settings.borderColor;
            default -> settings.shadowColor;
        };
        colorPicker.setValue(currentColor);
        colorPicker.setOnPick(color -> {
            switch (key) {
                case "bg" -> applyToAll(s -> s.backgroundColor = color);
                case "border" -> applyToAll(s -> s.borderColor = color);
                default -> applyToAll(s -> s.shadowColor = color);
            }
        });
    }

    public boolean handleDrag(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (!visible || selectedModule == null) return false;

        int x = screenWidth - WIDTH;
        int sectionX = x + PADDING;
        int sectionWidth = WIDTH - PADDING * 2;

        boolean any = scaleSlider.handleMouseDrag(mouseX, mouseY, sectionX, scaleY, sectionWidth);
        any |= opacitySlider.handleMouseDrag(mouseX, mouseY, sectionX, opacityY, sectionWidth);
        any |= bgOpacitySlider.handleMouseDrag(mouseX, mouseY, sectionX, bgOpacityY, sectionWidth);
        any |= borderThicknessSlider.handleMouseDrag(mouseX, mouseY, sectionX, borderThickY, sectionWidth);
        any |= shadowBlurSlider.handleMouseDrag(mouseX, mouseY, sectionX, shadowBlurY, sectionWidth);
        any |= fontSizeSlider.handleMouseDrag(mouseX, mouseY, sectionX, fontSizeY, sectionWidth);

        if (scaleSlider.isDragging()) {
            float scale = scaleSlider.getValue() / 100f;
            applyToAll(s -> s.scale = scale);
            syncScaleAll();
        }
        if (opacitySlider.isDragging()) {
            float opacity = opacitySlider.getValue() / 100f;
            applyToAll(s -> s.opacity = opacity);
        }
        if (bgOpacitySlider.isDragging()) {
            float bg = bgOpacitySlider.getValue() / 100f;
            applyToAll(s -> s.backgroundOpacity = bg);
        }
        if (borderThicknessSlider.isDragging()) {
            float thick = borderThicknessSlider.getValue();
            applyToAll(s -> s.borderThickness = thick);
        }
        if (shadowBlurSlider.isDragging()) {
            float blur = shadowBlurSlider.getValue();
            applyToAll(s -> s.shadowBlur = blur);
        }
        if (fontSizeSlider.isDragging()) {
            int size = (int)fontSizeSlider.getValue();
            applyToAll(s -> s.fontSize = size);
        }

        return any;
    }

    private void applyToAll(Consumer<HudProfile.ModuleSettings> action) {
        HudProfile profile = HudProfileManager.getInstance().getCurrentProfile();
        if (profile == null || settings == null) return;
        action.accept(settings);
        for (HudModule module : selectedModules) {
            if (module == selectedModule) continue;
            action.accept(profile.getModuleSettings(module.getId()));
        }
    }

    private void syncScaleAll() {
        HudProfile profile = HudProfileManager.getInstance().getCurrentProfile();
        if (profile == null) return;
        for (HudModule module : selectedModules) {
            if (!module.supportsScale()) continue;
            float scale = profile.getModuleSettings(module.getId()).scale;
            if (Math.abs(scale - 1f) < 0.005f) {
                module.setSize(null, null);
                HudSettings.setSize(module.getId(), null, null);
            } else {
                int w = Math.max(1, Math.round(module.getWidth() * scale));
                int h = Math.max(1, Math.round(module.getHeight() * scale));
                module.setSize(w, h);
                HudSettings.setSize(module.getId(), w, h);
            }
        }
        lastAppliedScale = settings.scale;
    }

    private void applyAnchor(int anchor) {
        int sw = net.minecraft.client.Minecraft.getInstance().getWindow() != null
                ? net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth() : 1920;
        int sh = net.minecraft.client.Minecraft.getInstance().getWindow() != null
                ? net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight() : 1080;

        for (HudModule m : selectedModules) {
            int rw = m.getRenderWidth();
            int rh = m.getRenderHeight();
            int nx = m.getX();
            int ny = m.getY();
            switch (anchor) {
                case 0 -> { nx = 10; ny = 10; }
                case 1 -> { nx = -rw - 10; ny = 10; }
                case 2 -> { nx = sw / 2 - rw / 2; ny = 10; }
                case 3 -> { nx = 10; ny = sh / 2 - rh / 2; }
                case 4 -> { nx = m.getX(); ny = sh - rh - 10; }
                case 5 -> { nx = -rw - 10; ny = sh / 2 - rh / 2; }
                case 6 -> { nx = sw / 2 - rw / 2; ny = sh - rh - 10; }
                case 7 -> { nx = 10; ny = sh - rh - 10; }
                case 8 -> { nx = -rw - 10; ny = sh - rh - 10; }
                default -> { }
            }
            m.setX(nx); m.setY(ny);
            HudSettings.setPosition(m.getId(), nx, ny);
        }
    }

    public void handleMouseUp() {
        scaleSlider.handleMouseUp();
        opacitySlider.handleMouseUp();
        bgOpacitySlider.handleMouseUp();
        borderThicknessSlider.handleMouseUp();
        shadowBlurSlider.handleMouseUp();
        fontSizeSlider.handleMouseUp();
    }

    public boolean handleScroll(double scrollDelta) {
        if (!visible || selectedModule == null) return false;
        int maxScroll = Math.max(0, contentHeight - 320);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollDelta * 16)));
        return true;
    }

    private void applySettings() {
        syncScaleAll();
        CrestModules.notify("Applied settings to " + selectedModules.size() + " module(s)");
    }

    private void resetSettings() {
        if (selectedModule == null) return;
        HudProfile.ModuleSettings fresh = new HudProfile.ModuleSettings();
        applyToAll(s -> {
            s.enabled = fresh.enabled;
            s.x = fresh.x;
            s.y = fresh.y;
            s.scale = fresh.scale;
            s.opacity = fresh.opacity;
            s.backgroundEnabled = fresh.backgroundEnabled;
            s.backgroundColor = fresh.backgroundColor;
            s.backgroundOpacity = fresh.backgroundOpacity;
            s.borderEnabled = fresh.borderEnabled;
            s.borderColor = fresh.borderColor;
            s.borderThickness = fresh.borderThickness;
            s.shadowEnabled = fresh.shadowEnabled;
            s.shadowColor = fresh.shadowColor;
            s.shadowBlur = fresh.shadowBlur;
            s.shadowOffsetX = fresh.shadowOffsetX;
            s.shadowOffsetY = fresh.shadowOffsetY;
            s.fontSize = fresh.fontSize;
            s.anchor = fresh.anchor;
        });

        for (HudModule module : selectedModules) {
            HudSettings.setPosition(module.getId(), 10, 10);
            HudSettings.setSize(module.getId(), null, null);
            module.setX(10); module.setY(10); module.setSize(null, null);
        }

        scaleSlider.setValue(100f);
        opacitySlider.setValue(100f);
        bgOpacitySlider.setValue(80f);
        borderThicknessSlider.setValue(1f);
        shadowBlurSlider.setValue(2f);
        fontSizeSlider.setValue(12f);
        anchorSelector.setSelectedAnchor(settings.anchor);
        lastAppliedScale = 1f;
        openPicker = null;

        CrestModules.notify("Reset " + selectedModules.size() + " module(s)");
    }

    private String getModuleIcon(String id) {
        String lower = id.toLowerCase();
        if (lower.contains("cps")) return "\u2694\uFE0F";
        if (lower.contains("armor")) return "\uD83D\uDEE1\uFE0F";
        if (lower.contains("zoom")) return "\uD83D\uDD0D";
        if (lower.contains("fps")) return "\uD83D\uDCCA";
        if (lower.contains("cat")) return "\uD83D\uDE3A";
        return "\uD83D\uDCE6";
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