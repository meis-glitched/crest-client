package com.crest.client.core;

import com.crest.client.ui.*;
import com.crest.client.ui.layout.ColumnLayout;
import com.crest.client.ui.layout.LayoutNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HudEditScreen extends Screen {

    private String selectedId;
    private boolean dragging;
    private boolean resizing;
    private int dragOffX;
    private int dragOffY;
    private boolean changed;

    private static final int HANDLE = 12;
    private static final int MIN_SIZE = 24;
    private static final int SNAP = 4;
    private static boolean snapEnabled = true;

    private final ScreenAnim anim = new ScreenAnim();
    private final Animated selectAnim = new Animated(0f, 12f);
    private final Animated handleAnim = new Animated(0f, 12f);
    private int mx, my;
    private Breakpoints.Size currentSize = Breakpoints.Size.MD;

    protected HudEditScreen() {
        super(Component.literal("Edit HUD"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new HudEditScreen());
    }

    private int rx(HudModule mod) {
        return mod.getX() < 0 ? width - mod.getRenderWidth() : mod.getX();
    }

    @Override
    protected void init() {
        Theme.load();
        anim.begin();
        selectAnim.set(0f);
        handleAnim.set(0f);
        currentSize = Breakpoints.getCurrentSize(width);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        this.mx = mx; this.my = my;
        Theme.tick(delta);
        anim.update(delta);
        selectAnim.tick(delta);
        handleAnim.tick(delta);

        Breakpoints.Size newSize = Breakpoints.getCurrentSize(width);
        if (newSize != currentSize) {
            currentSize = newSize;
        }

        int open = (int) (Theme.glassOpacity * anim.t());
        anim.blurBackdrop(g, width, height, ColorUtil.withAlpha(Theme.GLASS_BG, open));

        List<HudModule> modules = getHudModules();
        modules.sort(Comparator.comparingInt(m -> HudSettings.getOrder(m.getId())));
        boolean hasEnabled = false;

        for (HudModule mod : modules) {
            if (!CrestModules.isEnabled(mod.getId())) continue;
            hasEnabled = true;
            int rx = rx(mod);
            int ry = mod.getY();
            int rw = mod.getRenderWidth();
            int rh = mod.getRenderHeight();
            boolean selected = mod.getId().equals(selectedId);

            if (rw > 0 && rh > 0) {
                g.enableScissor(rx, ry, rx + rw, ry + rh);
                g.pose().pushMatrix();
                g.pose().translate(rx, ry);
                try { mod.render(g, minecraft, deltaTracker(delta)); } catch (Exception ignored) {}
                g.pose().popMatrix();
                g.disableScissor();
                g.fill(rx, ry, rx + rw, ry + rh, ColorUtil.withAlpha(Theme.GLASS_BG, 90));
            }

            int borderCol = selected ? ColorUtil.lerpARGB(Theme.BORDER_LIGHT, Theme.getAnimatedAccent(), selectAnim.get())
                                     : Theme.BORDER_LIGHT;
            Panel.drawHollowRect(g, rx - 2, ry - 2, rw + 4, rh + 4, borderCol);

            if (selected) {
                int hx = rx + rw + 4 - HANDLE;
                int hy = ry + rh + 4 - HANDLE;
                int ha = (int) (220 * handleAnim.get());
                Panel.draw(g, hx, hy, HANDLE, HANDLE, ColorUtil.withAlpha(Theme.getAnimatedAccent(), ha));
                g.fill(hx + 3, hy + 3, hx + HANDLE - 3, hy + 3 + 2, Theme.FOREGROUND);
                g.fill(hx + 3, hy + 6, hx + HANDLE - 3, hy + 6 + 2, Theme.FOREGROUND);
                g.fill(hx + 3, hy + 3, hx + 3 + 2, hy + HANDLE - 3, Theme.FOREGROUND);
                g.fill(hx + 6, hy + 3, hx + 6 + 2, hy + HANDLE - 3, Theme.FOREGROUND);
            }

            String modeSuffix = mod instanceof ArmorHudModule a ? " [" + a.getModeLabel() + "]" : "";
            String anchor = mod.getX() < 0 ? " \u2190" : "";
            String modLabel = "[" + mod.getName() + "]" + modeSuffix + anchor;
            Component label = Component.literal(modLabel);
            int lw = font.width(label);
            int labelY = Math.max(0, ry - 13);
            int labelBg = selected ? ColorUtil.withAlpha(Theme.getAnimatedAccent(), 220) : ColorUtil.withAlpha(Theme.GLASS_BG, 220);
            Panel.draw(g, rx, labelY, lw + 6, 11, labelBg);
            Panel.drawHollowRect(g, rx, labelY, lw + 6, 11, Theme.BORDER_LIGHT);
            g.text(font, label, rx + 3, labelY + 2, Theme.FOREGROUND);
        }

        if (!hasEnabled) {
            Component msg = Component.literal("No HUD modules enabled — enable them in the ClickGUI");
            g.centeredText(font, msg, width / 2, height / 2 - 10, Theme.ON_SURFACE_VARIANT);
        }

        if (selectedId != null) drawToolbar(g);

        Component hint = Component.literal("Click + drag to move  |  drag corner to resize  |  ESC to save & close");
        g.text(font, hint, (width - font.width(hint)) / 2, height - 16, Theme.TEXT_FAINT);
    }

    private void drawToolbar(GuiGraphicsExtractor g) {
        HudModule mod = findModule(selectedId);
        if (mod == null) return;

        boolean isXs = currentSize == Breakpoints.Size.XS;
        String[] labels;
        int bw;

        if (isXs) {
            labels = new String[]{"\u21BA", "\u2413", "\u25B2", "\u25A1", "\u21BB"};
            bw = 40;
        } else {
            labels = new String[]{"Reset", CrestModules.isEnabled(mod.getId()) ? "Hide" : "Show", "Front",
                    snapEnabled ? "Snap:On" : "Snap:Off", "Reset All"};
            bw = 66;
        }
        int bh = 24;
        int gap = Math.min(Spacing.S2, 4);
        int total = bw * labels.length + gap * (labels.length - 1);
        int bx = Math.max(Spacing.S2, (width - total) / 2);
        int by = height - 46;

        for (int i = 0; i < labels.length; i++) {
            int x = bx + i * (bw + gap);
            boolean hover = mx >= x && mx <= x + bw && my >= by && my <= by + bh;
            int fill = hover ? ColorUtil.withAlpha(Theme.getAnimatedAccent(), 220) : ColorUtil.withAlpha(Theme.CARD, 230);
            Panel.draw(g, x, by, bw, bh, fill);
            Panel.drawHollowRect(g, x, by, bw, bh, Theme.BORDER_LIGHT);
            g.text(font, Component.literal(labels[i]), x + (bw - font.width(labels[i])) / 2, by + (bh - font.lineHeight) / 2, Theme.FOREGROUND);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int btn = event.buttonInfo().input();
        if (btn != 0) return super.mouseClicked(event, doubleClick);

        if (selectedId != null) {
            boolean isXs = currentSize == Breakpoints.Size.XS;
            String[] labels;
            int bw;

            if (isXs) {
                labels = new String[]{"\u21BA", "\u2413", "\u25B2", "\u25A1", "\u21BB"};
                bw = 40;
            } else {
                labels = new String[]{"Reset", CrestModules.isEnabled(selectedId) ? "Hide" : "Show", "Front",
                        snapEnabled ? "Snap:On" : "Snap:Off", "Reset All"};
                bw = 66;
            }
            int bh = 24;
            int gap = Math.min(Spacing.S2, 4);
            int total = bw * labels.length + gap * (labels.length - 1);
            int bx = Math.max(Spacing.S2, (width - total) / 2);
            int by = height - 46;

            for (int i = 0; i < labels.length; i++) {
                int x = bx + i * (bw + gap);
                if (mx >= x && mx <= x + bw && my >= by && my <= by + bh) {
                    handleToolbar(i);
                    return true;
                }
            }
        }

        HudModule sel = selectedId != null ? findModule(selectedId) : null;
        if (sel != null && CrestModules.isEnabled(sel.getId())) {
            int rx = rx(sel);
            int ry = sel.getY();
            int rw = sel.getRenderWidth();
            int rh = sel.getRenderHeight();
            int hx = rx + rw + 4 - HANDLE;
            int hy = ry + rh + 4 - HANDLE;
            if (mx >= hx && mx <= hx + HANDLE && my >= hy && my <= hy + HANDLE) {
                resizing = true;
                return true;
            }
        }

        for (HudModule mod : getHudModules()) {
            if (!CrestModules.isEnabled(mod.getId())) continue;
            int rx = rx(mod);
            int ry = mod.getY();
            int rw = mod.getRenderWidth();
            int rh = mod.getRenderHeight();
            if (mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh) {
                if (!mod.getId().equals(selectedId)) {
                    selectedId = mod.getId();
                    selectAnim.set(1f);
                    handleAnim.set(1f);
                }
                dragging = true;
                dragOffX = (int) (mx - rx);
                dragOffY = (int) (my - ry);
                HudSettings.bringToFront(mod.getId());
                return true;
            }
        }
        selectedId = null;
        selectAnim.set(0f);
        handleAnim.set(0f);
        return super.mouseClicked(event, doubleClick);
    }

    private void handleToolbar(int i) {
        HudModule mod = findModule(selectedId);
        if (mod == null) return;
        switch (i) {
            case 0 -> {
                HudSettings.setPosition(selectedId, 10, 10);
                HudSettings.setSize(selectedId, null, null);
                mod.setX(10); mod.setY(10); mod.setSize(null, null);
                changed = true;
            }
            case 1 -> CrestModules.setEnabled(selectedId, !CrestModules.isEnabled(selectedId));
            case 2 -> HudSettings.bringToFront(selectedId);
            case 3 -> snapEnabled = !snapEnabled;
            case 4 -> {
                for (HudModule m : getHudModules()) {
                    HudSettings.setPosition(m.getId(), 10, 10);
                    HudSettings.setSize(m.getId(), null, null);
                    m.setX(10); m.setY(10); m.setSize(null, null);
                }
                changed = true;
            }
        }
    }

    private static int snap(int v) {
        return snapEnabled ? (v / SNAP) * SNAP : v;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (resizing && selectedId != null) {
            HudModule mod = findModule(selectedId);
            if (mod != null) {
                int rx = rx(mod);
                int ry = mod.getY();
                int newW = snap((int) Math.max(MIN_SIZE, Math.min(width - rx, event.x() - rx)));
                int newH = snap((int) Math.max(MIN_SIZE, Math.min(height - ry, event.y() - ry)));
                mod.setSize(newW, newH);
                HudSettings.setSize(selectedId, newW, newH);
                changed = true;
            }
            return true;
        }
        if (dragging && selectedId != null) {
            HudModule mod = findModule(selectedId);
            if (mod != null) {
                int newX = snap((int) Math.max(0, event.x() - dragOffX));
                int newY = snap((int) Math.max(0, event.y() - dragOffY));
                if (mod.getX() < 0) newX = -(mod.getRenderWidth() + 10);
                mod.setX(newX);
                mod.setY(newY);
                HudSettings.setPosition(selectedId, newX, newY);
                changed = true;
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        resizing = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256 || event.key() == 342) {
            if (changed) HudSettings.save();
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        anim.closeTo(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<HudModule> getHudModules() {
        List<HudModule> list = new ArrayList<>();
        for (CrestModule m : CrestModules.getAll().values()) {
            if (m instanceof HudModule h) list.add(h);
        }
        return list;
    }

    private HudModule findModule(String id) {
        CrestModule m = CrestModules.get(id);
        return m instanceof HudModule h ? h : null;
    }

    private static DeltaTracker deltaTracker(float delta) {
        float d = delta;
        return new DeltaTracker() {
            @Override public float getGameTimeDeltaTicks() { return d; }
            @Override public float getGameTimeDeltaPartialTick(boolean ignore) { return d; }
            @Override public float getRealtimeDeltaTicks() { return d; }
        };
    }
}
