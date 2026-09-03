package com.crest.client.core;

import com.crest.client.ui.Anim;
import com.crest.client.ui.ColorUtil;
import com.crest.client.ui.Panel;
import com.crest.client.ui.Slider;
import com.crest.client.ui.Theme;
import com.crest.client.ui.Tween;
import com.crest.client.ui.UiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class AnimationsScreen extends Screen {
    private static final String CONFIG_SECTION = "crest_client";
    private static final String CONFIG_KEY = "menu_anim_duration";
    private static final String ENABLE_KEY = "menu_anim_enabled";
    private static final String EASING_KEY = "menu_anim_easing";
    private static final float MIN_DURATION = 0.2f;
    private static final float MAX_DURATION = 1.0f;
    private static final float DEFAULT_DURATION = 0.45f;
    private static final String[] EASING_STYLES = {"Back", "Cubic", "Expo", "Sine", "Quad", "Quart"};

    private final Screen parent;
    private final ScreenAnim anim = new ScreenAnim();
    private final Slider durationSlider;
    private final Tween preview = new Tween(DEFAULT_DURATION);
    private boolean previewForward = true;
    private boolean previewStarted;
    private int mx, my;
    private int sliderX, sliderY, sliderW;
    private int enableX, enableY, enableW;
    private int easingBtnX, easingBtnY, easingBtnW = 110, easingBtnH = 24;

    public AnimationsScreen(Screen parent) {
        super(Component.literal("Animations"));
        this.parent = parent;
        anim.begin();
        durationSlider = new Slider(MIN_DURATION, MAX_DURATION, getMenuAnimDuration(), this::onDurationChanged);
    }

    public static void open(Screen parent) {
        Minecraft.getInstance().setScreen(new AnimationsScreen(parent));
    }

    public static float getMenuAnimDuration() {
        try {
            float v = CrestModules.getConfigManager().getFloat(CONFIG_SECTION, CONFIG_KEY);
            if (v >= MIN_DURATION && v <= MAX_DURATION) return v;
        } catch (Exception ignored) {}
        return DEFAULT_DURATION;
    }

    public static boolean isMenuAnimEnabled() {
        try {
            ConfigManager cfg = CrestModules.getConfigManager();
            if (cfg.has(CONFIG_SECTION, ENABLE_KEY)) return cfg.getBoolean(CONFIG_SECTION, ENABLE_KEY);
        } catch (Exception ignored) {}
        return true;
    }

    public static String getMenuAnimEasing() {
        try {
            String s = CrestModules.getConfigManager().getString(CONFIG_SECTION, EASING_KEY);
            for (String style : EASING_STYLES) {
                if (style.equalsIgnoreCase(s)) return style;
            }
        } catch (Exception ignored) {}
        return EASING_STYLES[0];
    }

    private void onDurationChanged(float v) {
        CrestModules.getConfigManager().set(CONFIG_SECTION, CONFIG_KEY, v);
    }

    @Override
    protected void init() {
        Theme.load();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        this.mx = mx;
        this.my = my;
        Theme.tick(delta);
        anim.update(delta);

        int pX = 40, pY = 40, pW = width - 80, pH = height - 80;

        anim.backdrop(g, width, height);
        int wy = anim.slideY(-12);
        g.pose().pushMatrix();
        g.pose().translate(0, wy);
        Panel.draw(g, pX, pY, pW, pH, Theme.GLASS_BG);
        Panel.drawHollowRect(g, pX, pY, pW, pH, Theme.BORDER_LIGHT);

        int accent = Theme.getAnimatedAccent();
        int cx = pX + 20;
        g.text(font, Component.literal("Animations"), cx, pY + 16, Theme.FOREGROUND);
        g.text(font, Component.literal("Controls how Crest screens open and close."),
            cx, pY + 32, Theme.MUTED_FOREGROUND);

        int rowY = pY + 60;
        g.fill(cx, rowY, cx + 3, rowY + font.lineHeight + 2, accent);
        g.text(font, Component.literal("Animation Duration"), cx + 8, rowY, Theme.FOREGROUND);
        rowY += font.lineHeight + 10;

        sliderX = cx;
        sliderY = rowY;
        sliderW = Math.min(pW - 160, 320);

        float v = durationSlider.getValue();
        String tier = v < 0.4f ? "Slow" : (v <= 0.7f ? "Balanced" : "Snappy");
        String valLabel = String.format("%.2f s", v) + "  (" + tier + ")";
        durationSlider.render(g, font, sliderX, sliderY, sliderW, mx, my, delta);
        int sliderH = durationSlider.getHeight();
        g.text(font, Component.literal(valLabel), sliderX + sliderW + 12,
            sliderY + (sliderH - font.lineHeight) / 2, accent);
        rowY += sliderH + 8;

        g.text(font, Component.literal("Longer values make screens open and close more slowly."),
            cx, rowY, ColorUtil.withAlpha(Theme.MUTED_FOREGROUND, 170));
        rowY += 28;

        int cw = Math.min(pW - 40, 420);

        enableX = cx;
        enableY = rowY;
        enableW = cw;
        drawToggle(g, "Enable Menu Animation", enableX, enableY, enableW, isMenuAnimEnabled(), accent);
        rowY += 40;

        g.text(font, Component.literal("Easing Style"), cx, rowY + 5, Theme.FOREGROUND);
        easingBtnX = cx + cw - easingBtnW;
        easingBtnY = rowY;
        String current = getMenuAnimEasing();
        boolean easeHover = mx >= easingBtnX && mx <= easingBtnX + easingBtnW
            && my >= easingBtnY && my <= easingBtnY + easingBtnH;
        int fill = easeHover ? ColorUtil.withAlpha(Theme.MUTED, 130) : ColorUtil.withAlpha(Theme.BACKGROUND, 140);
        Panel.draw(g, easingBtnX, easingBtnY, easingBtnW, easingBtnH, fill);
        Panel.drawHollowRect(g, easingBtnX, easingBtnY, easingBtnW, easingBtnH, Theme.BORDER_LIGHT);
        String btnLabel = current + " \u25B8";
        g.text(font, Component.literal(btnLabel), easingBtnX + (easingBtnW - font.width(btnLabel)) / 2,
            easingBtnY + (easingBtnH - font.lineHeight) / 2, easeHover ? accent : Theme.FOREGROUND);
        rowY += 36;

        g.fill(cx, rowY, cx + 3, rowY + font.lineHeight + 2, accent);
        g.text(font, Component.literal("Preview"), cx + 8, rowY, Theme.FOREGROUND);
        rowY += font.lineHeight + 10;

        boolean animOn = isMenuAnimEnabled();
        if (animOn) {
            if (!previewStarted) {
                previewStarted = true;
                preview.setDuration(v);
                preview.setEasing(current);
                preview.startForward();
            }
            preview.setDuration(v);
            preview.setEasing(current);
            preview.tick(delta);
            if (previewForward && preview.finishedForward()) {
                previewForward = false;
                preview.startReverse();
            } else if (!previewForward && preview.finishedReverse()) {
                previewForward = true;
                preview.startForward();
            }
        }
        int trackW = Math.min(pW - 40, 420);
        int trackH = 14;
        g.fill(cx, rowY, cx + trackW, rowY + trackH, ColorUtil.withAlpha(Theme.MUTED, 120));
        Panel.drawHollowRect(g, cx, rowY, trackW, trackH, Theme.BORDER_LIGHT);
        float previewFill = animOn ? Anim.clamp(preview.value(), 0f, 1f) : 1f;
        int fillW = (int) ((trackW - 2) * previewFill);
        if (fillW > 0) g.fill(cx + 1, rowY + 1, cx + 1 + fillW, rowY + trackH - 1, animOn ? accent : Theme.MUTED);
        if (!animOn) {
            g.text(font, Component.literal("Animations are off"), cx + trackW + 12,
                rowY + (trackH - font.lineHeight) / 2, Theme.MUTED_FOREGROUND);
        }

        g.text(font, Component.literal("ESC to go back"),
            cx, height - 56, Theme.MUTED_FOREGROUND);

        g.pose().popMatrix();
    }

    private void drawToggle(GuiGraphicsExtractor g, String label, int x, int y, int w, boolean on, int accent) {
        g.text(font, Component.literal(label), x, y + 5, Theme.FOREGROUND);
        int tw = 44, th = 24;
        int tx = x + w - tw;
        int ty = y;
        int trackColor = on ? ColorUtil.withAlpha(accent, 210) : ColorUtil.withAlpha(Theme.MUTED, 170);
        Panel.draw(g, tx, ty, tw, th, trackColor);
        Panel.drawHollowRect(g, tx, ty, tw, th, on ? accent : Theme.BORDER_LIGHT);
        int knobX = on ? tx + tw - 20 : tx + 4;
        g.fill(knobX, ty + 4, knobX + 16, ty + th - 4, on ? 0xFFFFFFFF : ColorUtil.withAlpha(Theme.MUTED_FOREGROUND, 200));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mxx = event.x(), myy = event.y();
        int btn = event.buttonInfo().input();
        if (btn == 0 && mxx >= sliderX && mxx <= sliderX + sliderW
            && myy >= sliderY - 4 && myy <= sliderY + durationSlider.getHeight() + 4) {
            durationSlider.mouseClicked(mxx, myy, btn);
            return true;
        }

        if (btn == 0 && mxx >= enableX && mxx <= enableX + enableW && myy >= enableY && myy <= enableY + 28) {
            UiSounds.click();
            ConfigManager cfg = CrestModules.getConfigManager();
            boolean next = !isMenuAnimEnabled();
            cfg.set(CONFIG_SECTION, ENABLE_KEY, next);
            cfg.save();
            return true;
        }
        if (btn == 0 && mxx >= easingBtnX && mxx <= easingBtnX + easingBtnW
            && myy >= easingBtnY && myy <= easingBtnY + easingBtnH) {
            UiSounds.click();
            String cur = getMenuAnimEasing();
            String next = EASING_STYLES[(java.util.Arrays.asList(EASING_STYLES).indexOf(cur) + 1) % EASING_STYLES.length];
            ConfigManager cfg = CrestModules.getConfigManager();
            cfg.set(CONFIG_SECTION, EASING_KEY, next);
            cfg.save();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (durationSlider.dragging) {
            durationSlider.mouseDragged(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        durationSlider.stopDrag();
        CrestModules.getConfigManager().save();
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        CrestModules.getConfigManager().save();
        anim.closeTo(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
