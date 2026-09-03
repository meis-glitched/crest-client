package com.crest.client.core;

import com.crest.client.ui.Anim;
import com.crest.client.ui.ColorUtil;
import com.crest.client.ui.Theme;
import com.crest.client.ui.Tween;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * Shared open/close choreography for Crest screens: a config-driven (duration,
 * easing, on/off) entrance and a mirrored exit that hands control back to a
 * target screen once the close animation finishes.
 *
 * <p>Screens own their visuals; this manages timing ({@link #value()} /
 * {@link #t()}), the routing of the pending screen swap on close, and the
 * animated glass backdrop ({@link #backdrop}).
 */
public final class ScreenAnim {
    private final Tween tween = new Tween(0.45f);
    private boolean enabled = true;
    private boolean closing;
    private boolean swapped;
    private Screen target;

    public ScreenAnim() {
        updateConfig();
    }

    /** Re-read the user animation config (speed, easing, on/off). */
    public void updateConfig() {
        enabled = AnimationsScreen.isMenuAnimEnabled();
        tween.setDuration(AnimationsScreen.getMenuAnimDuration());
        tween.setEasing(AnimationsScreen.getMenuAnimEasing());
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Start the entrance. Call from the screen's constructor or {@code init()}. */
    public void begin() {
        closing = false;
        swapped = false;
        target = null;
        if (enabled) {
            tween.startForward();
        } else {
            tween.startFinished();
        }
    }

    /**
     * Request the exit. When animations are disabled this swaps instantly;
     * otherwise the screen keeps rendering until the close finishes and
     * {@code update} routes the swap. Repeated calls while already closing just
     * retarget the pending screen; calls after the swap are ignored (the game
     * may invoke {@code onClose()} again when the screen is removed).
     */
    public void closeTo(Screen next) {
        if (swapped) return;
        if (!enabled) {
            swapped = true;
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(next);
            return;
        }
        if (closing) {
            target = next;
            return;
        }
        target = next;
        closing = true;
        tween.startReverse();
    }

    /**
     * Advance the animation. Returns the eased open value (0..1, may overshoot
     * for back-style easing). Handles the pending {@link Screen} swap once a
     * close animation reaches zero.
     */
    public float update(float delta) {
        if (!enabled) return 1f;
        tween.tick(delta);
        if (closing && tween.finishedReverse()) {
            closing = false;
            swapped = true;
            Screen next = target;
            target = null;
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(next);
        }
        return tween.value();
    }

    public float value() {
        return enabled ? tween.value() : 1f;
    }

    /** Clamped open value (0..1); hides any overshoot past the endpoints. */
    public float t() {
        return Anim.clamp(value(), 0f, 1f);
    }

    public boolean isClosing() {
        return closing;
    }

    public boolean finishedOpening() {
        return !closing && tween.finishedForward();
    }

    /** True while the entrance has not fully settled; used to swallow input. */
    public boolean isBusy() {
        return enabled && !finishedOpening();
    }

    /** Vertical slide helper: {@code slideY(16)} is 16px below rest at 0, 0 at rest. */
    public int slideY(float amount) {
        return (int) ((1f - t()) * amount);
    }

    /** Animated glass backdrop covering the whole screen. */
    public void backdrop(GuiGraphicsExtractor g, int width, int height) {
        blurBackdrop(g, width, height,
            ColorUtil.withAlpha(Theme.GLASS_BG, (int) (Theme.glassOpacity * t())));
    }

    /**
     * Animated glass backdrop with the optional stratified backdrop blur.
     * When {@link Theme#menuBlur} is active the content drawn before this
     * stratum (the world / previous screen) is blurred by {@link Theme#menuBlurRadius}
     * using the game's built-in post-chain backdrop pass.
     */
    public void blurBackdrop(GuiGraphicsExtractor g, int width, int height, int glass) {
        if (Theme.menuBlur && Theme.menuBlurRadius > 0f) {
            try {
                Minecraft mc = Minecraft.getInstance();
                int prev = mc.options.getMenuBackgroundBlurriness();
                mc.options.menuBackgroundBlurriness().set((int) Theme.menuBlurRadius);
                g.blurBeforeThisStratum();
                mc.options.menuBackgroundBlurriness().set(prev);
            } catch (Exception ignored) {}
        }
        g.fill(0, 0, width, height, glass);
    }
}