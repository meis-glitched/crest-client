package com.crest.client.core;

import com.crest.client.core.hud.profile.HudProfile;
import com.crest.client.core.hud.profile.HudProfileManager;
import com.crest.client.ui.Breakpoints;
import com.crest.client.ui.ColorUtil;
import com.crest.client.ui.Panel;
import com.crest.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class HudModule implements CrestModule, RenderableModule {
    protected int x;
    protected int y;

    private Integer overrideW;
    private Integer overrideH;

    protected HudModule(int defaultX, int defaultY) {
        this.x = defaultX;
        this.y = defaultY;
    }

    public abstract void render(GuiGraphicsExtractor g, Minecraft mc, DeltaTracker d);
    public abstract int getWidth();
    public abstract int getHeight();

    public int getX() { return x; }
    public int getY() { return y; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }

    public Integer getOverrideW() { return overrideW; }
    public Integer getOverrideH() { return overrideH; }

    public void setSize(Integer w, Integer h) {
        this.overrideW = w;
        this.overrideH = h;
    }

    public int getRenderWidth() {
        return overrideW != null ? overrideW : getWidth();
    }

    public int getRenderHeight() {
        return overrideH != null ? overrideH : getHeight();
    }

    public void loadSettings() {}

    private static final float MIN_SCALE = 0.3f;
    private static final float MAX_SCALE = 4.0f;

    protected int getSafeLeft() {
        Minecraft mc = Minecraft.getInstance();
        int margin = 4;
        if (mc.getWindow() != null && Breakpoints.isXsOrSmaller(mc.getWindow().getGuiScaledWidth())) {
            margin = 8;
        }
        return margin;
    }

    protected int getSafeRight() {
        Minecraft mc = Minecraft.getInstance();
        int margin = 4;
        if (mc.getWindow() != null && Breakpoints.isXsOrSmaller(mc.getWindow().getGuiScaledWidth())) {
            margin = 8;
        }
        return mc.getWindow() != null ? mc.getWindow().getGuiScaledWidth() - margin : margin;
    }

    protected int getSafeTop() {
        return Breakpoints.isXsOrSmaller(width()) ? 8 : 4;
    }

    protected int getSafeBottom() {
        Minecraft mc = Minecraft.getInstance();
        int margin = Breakpoints.isXsOrSmaller(width()) ? 8 : 4;
        return mc.getWindow() != null ? mc.getWindow().getGuiScaledHeight() - margin : margin;
    }

    private static int width() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getWindow() != null ? mc.getWindow().getGuiScaledWidth() : 1920;
    }

    /**
     * Which properties this module can display/apply in the HUD editor's
     * properties panel. Subclasses override to narrow what makes sense.
     */
    public boolean supportsScale() { return true; }

    public boolean supportsOpacity() { return true; }

    public boolean supportsChrome() { return true; }

    /** Runtime look source for the module, from the active HUD profile. */
    protected HudProfile.ModuleSettings profileSettings() {
        HudProfileManager mgr = HudProfileManager.getInstance();
        mgr.ensureLoaded();
        HudProfile profile = mgr.getCurrentProfile();
        return profile != null ? profile.getModuleSettings(getId()) : null;
    }

    /** Draws the generic background/border/shadow behind a module box. */
    public static void renderChrome(GuiGraphicsExtractor g, int x, int y, int w, int h, HudProfile.ModuleSettings s) {
        if (w <= 0 || h <= 0) return;
        float opacity = Math.max(0f, Math.min(1f, s.opacity));

        if (s.shadowEnabled) {
            int blur = Math.max(0, (int) Math.round(Math.min(10f, s.shadowBlur)));
            int steps = Math.max(1, blur);
            for (int i = blur; i >= 1; i--) {
                float t = (steps - i + 1) / (float) steps;
                int a = (int) (30 * t * opacity);
                Panel.draw(g, x + s.shadowOffsetX - i, y + s.shadowOffsetY - i,
                        w + i * 2, h + i * 2, ColorUtil.withAlpha(s.shadowColor, a));
            }
        }

        if (s.backgroundEnabled) {
            int a = (int) (205 * Math.max(0f, Math.min(1f, s.backgroundOpacity)) * opacity);
            Panel.draw(g, x, y, w, h, ColorUtil.withAlpha(s.backgroundColor, a));
        }

        if (s.borderEnabled) {
            int thickness = Math.max(1, (int) Math.round(Math.max(0f, s.borderThickness)));
            int a = (int) (255 * opacity);
            for (int i = 0; i < thickness; i++) {
                Panel.drawHollowRect(g, x - i, y - i, w + i * 2, h + i * 2, ColorUtil.withAlpha(s.borderColor, a));
            }
        }
    }

    public void renderScaled(GuiGraphicsExtractor g, Minecraft mc, DeltaTracker d) {
        int natW = getWidth();
        int natH = getHeight();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int rw = getRenderWidth();
        int rh = getRenderHeight();
        int rx = x < 0 ? screenW - rw : x;
        int ry = y;

        if (supportsChrome()) {
            HudProfile.ModuleSettings s = profileSettings();
            if (s != null) renderChrome(g, rx, ry, rw, rh, s);
        }

        if (overrideW == null && overrideH == null) {
            render(g, mc, d);
            return;
        }
        if (natW <= 0 || natH <= 0) {
            render(g, mc, d);
            return;
        }
        float sx = (float) getRenderWidth() / natW;
        float sy = (float) getRenderHeight() / natH;
        float s = Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.min(sx, sy)));

        s = Math.min(s, screenW < 800 ? 1 : screenW < 1200 ? 2 : 4);

        g.pose().pushMatrix();
        g.pose().translate(rx, ry);
        g.pose().scale(s);
        g.pose().translate(-rx, -ry);
        render(g, mc, d);
        g.pose().popMatrix();
    }

    public boolean isOnScreen(int screenW, int screenH) {
        int rx = x < 0 ? screenW - getRenderWidth() : x;
        int ry = y;
        return rx < screenW && ry < screenH && rx + getRenderWidth() > 0 && ry + getRenderHeight() > 0;
    }

    @Override
    public String getCategory() { return "HUD"; }
}
