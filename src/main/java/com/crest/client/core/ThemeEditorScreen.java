package com.crest.client.core;

import com.crest.client.ui.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ThemeEditorScreen extends Screen {
    private final Screen parent;

    private static final int PRESET_CARD_H = 36;
    private static final int PRESETS_COUNT = 4;
    private static final int PRESETS_H = PRESETS_COUNT * (PRESET_CARD_H + Spacing.S2);
    private static final int FOOTER_H = 44;
    private static final int HEADER_H = 56;

    private ThemeData work = Theme.get().clone();
    private ColorPicker colorPicker;
    private int panelX, panelY, panelW, panelH;
    private int railX, railY, railW, railH;
    private int listX, listY, listW, listH;
    private int footerY;

    private final List<Widget> items = new ArrayList<>();
    private final Map<String, Boolean> sectionExpanded = new HashMap<>();
    private final Animated colorAnim = new Animated(0f, 12f);
    private final ScreenAnim anim = new ScreenAnim();
    private final ScrollContainer scroll = new ScrollContainer();

    private int mx, my;
    private String message = "";
    private int messageTimer;
    private Breakpoints.Size currentSize = Breakpoints.Size.MD;

    private boolean collapsedPreview = false;

    public ThemeEditorScreen(Screen parent) {
        super(Component.literal("Theme Editor"));
        this.parent = parent;
        anim.begin();
        sectionExpanded.put("Colors", true);
        sectionExpanded.put("Appearance", true);
        sectionExpanded.put("Blur", true);
        sectionExpanded.put("Layout", true);
        buildItems();
    }

    /** Items can expose a short description shown as a hover tooltip. */
    private interface Tip {
        String tip();
    }

    private void buildItems() {
        items.clear();
        scroll.scrollOffset = 0;
        scroll.scrollTarget = 0;

        addSection("Colors", true, "Colors used across the entire interface",
            new ThemeColorItem("Accent", "Primary color for highlights, buttons, and sliders",
                () -> work.accent, c -> work.accent = c),
            new ThemeColorItem("Background", "Base color behind all screens",
                () -> work.background, c -> work.background = c),
            new ThemeColorItem("Foreground", "Primary text color",
                () -> work.foreground, c -> work.foreground = c),
            new ThemeColorItem("Card", "Surface color for panels and cards",
                () -> work.card, c -> work.card = c),
            new ThemeColorItem("Muted Text", "Secondary, subdued text color",
                () -> work.mutedForeground, c -> work.mutedForeground = c),
            new ThemeColorItem("Destructive", "Color for errors and destructive actions",
                () -> work.destructive, c -> work.destructive = c),
            new ThemeColorItem("Border", "Color for outlines and dividers",
                () -> work.border, c -> work.border = c),
            new ThemeColorItem("Glass Tint", "Tint color for glassmorphic overlays",
                () -> work.glassBg, c -> work.glassBg = c),
            new ThemeColorItem("Sidebar", "Background of the navigation sidebar",
                () -> work.sidebarBg, c -> work.sidebarBg = c));

        addSection("Appearance", false, "Rounded corners, opacity, and text sizing",
            new ThemeSliderItem("Glass Opacity", "Transparency of the backdrop overlay", 0, 255,
                () -> work.glassOpacity, v -> work.glassOpacity = v),
            new ThemeSliderItem("Corner Radius", "How round panels and buttons are", 0, 24,
                () -> work.radius, v -> work.radius = v),
            new ThemeSliderItem("Font Scale", "Scales the UI text size", 70, 150,
                () -> (int) (work.fontScale * 100), v -> work.fontScale = v / 100f, v -> v + "%"),
            new ThemeToggleItem("Animated Accent", "Slowly cycles the accent color hue",
                () -> work.accentAnim, v -> work.accentAnim = v));

        addSection("Blur", false, "Backdrop blur behind menus and overlays",
            new ThemeToggleItem("Menu Blur", "Blurs the content behind menus",
                () -> work.menuBlur, v -> work.menuBlur = v),
            new ThemeSliderItem("Blur Radius", "Strength of the backdrop blur", 1, 20,
                () -> (int) work.menuBlurRadius, v -> work.menuBlurRadius = v));

        addSection("Layout", false, "Spacing and density preferences",
            new ThemeModeItem("Density", "Controls the compactness of UI rows",
                new String[]{"Compact", "Normal", "Comfortable"},
                () -> work.density.ordinal(), i -> work.density = Theme.Density.values()[i]));

        scroll.children(items);
    }

    private void addSection(String name, boolean collapsible, String tooltip, Widget... sectionItems) {
        items.add(new ThemeHeader(name, collapsible, Boolean.TRUE.equals(sectionExpanded.get(name)), tooltip));
        if (!collapsible || Boolean.TRUE.equals(sectionExpanded.get(name))) {
            items.addAll(Arrays.asList(sectionItems));
        }
    }

    @Override
    protected void init() {
        currentSize = Breakpoints.getCurrentSize(width);
        computeLayout();
    }

    private void computeLayout() {
        boolean isCompact = currentSize == Breakpoints.Size.XS || currentSize == Breakpoints.Size.SM;

        panelW = isCompact ? Math.min(width - Spacing.S4 * 2, width) : Math.min(620, width - Spacing.S10);
        panelX = (width - panelW) / 2;
        panelY = Spacing.S4;
        panelH = height - Spacing.S6;

        collapsedPreview = isCompact;

        // Reserve the footer zone so the rail and list never collide with buttons.
        footerY = panelY + panelH - FOOTER_H;
        int bodyBottom = footerY - Spacing.S2;

        if (collapsedPreview) {
            railX = panelX + Spacing.S3;
            railY = panelY + 46;
            railW = panelW - Spacing.S6;
            railH = PRESETS_H;

            listX = panelX + Spacing.S3;
            listY = railY + railH + Spacing.S8;
            listW = panelW - Spacing.S6;
            listH = Math.max(80, bodyBottom - listY);
        } else {
            railX = panelX + Spacing.S4;
            railY = panelY + HEADER_H;
            railW = 200;
            railH = Math.max(1, bodyBottom - railY);

            listX = railX + railW + Spacing.S6;
            listY = panelY + HEADER_H;
            listW = panelX + panelW - Spacing.S4 - listX;
            listH = Math.max(80, bodyBottom - listY);
        }
        scroll.h = listH;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        this.mx = mx; this.my = my;
        Theme.tick(delta);
        float open = anim.update(delta);
        colorAnim.tick(delta);
        if (messageTimer > 0) messageTimer--;

        Breakpoints.Size newSize = Breakpoints.getCurrentSize(width);
        if (newSize != currentSize) {
            currentSize = newSize;
            computeLayout();
        }

        if (open < 0.01f && !anim.isClosing()) return;

        anim.blurBackdrop(g, width, height,
            ColorUtil.withAlpha(work.glassBg, (int) (work.glassOpacity * anim.t())));
        int wy = anim.slideY(16);
        g.pose().pushMatrix();
        g.pose().translate(0, wy);

        Panel.drawElevated(g, panelX, panelY, panelW, panelH, ColorUtil.withAlpha(work.card, 235), Theme.ELEVATION_3);
        g.fill(panelX + 3, panelY + 2, panelX + panelW - 3, panelY + 3, ColorUtil.withAlpha(work.accent, Theme.topStripAlpha));

        g.text(font, Component.literal("Theme Editor"), panelX + Spacing.S4, panelY + Spacing.S3, work.foreground);
        if (!collapsedPreview) {
            g.text(font, Component.literal("Customize the look  \u2022  Apply to save"),
                panelX + Spacing.S4, panelY + Spacing.S3 + font.lineHeight + 2, ColorUtil.withAlpha(work.mutedForeground, 200));
        }

        g.enableScissor(railX, railY, railX + railW, railY + railH);
        drawPresets(g);
        if (!collapsedPreview) drawPreview(g);
        g.disableScissor();

        drawSettingsList(g, delta);
        drawFooter(g);

        if (messageTimer > 0) {
            int mw = font.width(message) + Spacing.S4;
            int myy = footerY - Spacing.S4 - 26;
            Panel.draw(g, panelX + panelW / 2 - mw / 2, myy, mw, 26, ColorUtil.withAlpha(work.popover, 240));
            g.text(font, Component.literal(message), panelX + panelW / 2 - font.width(message) / 2, myy + 6, work.foreground);
        }

        if (colorPicker != null) {
            colorAnim.set(1f);
            int pw = Breakpoints.isXsOrSmaller(width) ? Math.min(200, width - Spacing.S2 * 2) : 220;
            int ph = 260;
            int px = Math.max(Spacing.S2, Math.min(width / 2 - pw / 2, width - pw - Spacing.S2));
            int py = Spacing.S4;
            Panel.drawElevated(g, px, py, pw, ph, ColorUtil.withAlpha(work.card, 255), Theme.ELEVATION_3);
            g.fill(px + 2, py, px + pw - 2, py + 1, ColorUtil.withAlpha(work.accent, Theme.topStripAlpha));
            colorPicker.layout(px, py, pw, ph);
            colorPicker.render(g, font, mx, my, (int) (255 * colorAnim.get()));
        } else {
            colorAnim.set(0f);
        }

        g.pose().popMatrix();
    }

    private void drawPresets(GuiGraphicsExtractor g) {
        String[] names = {"Lunar", "Dark", "Light", "Amoled"};
        ThemeData[] presets = {ThemePresets.LUNAR, ThemePresets.DARK, ThemePresets.LIGHT, ThemePresets.AMOLED};
        for (int i = 0; i < PRESETS_COUNT; i++) {
            int x = railX, y = railY + i * (PRESET_CARD_H + Spacing.S2), w = railW;
            boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + PRESET_CARD_H;
            boolean active = work.preset.equalsIgnoreCase(names[i]);
            Panel.draw(g, x, y, w, PRESET_CARD_H, ColorUtil.withAlpha(presets[i].card, hover ? 255 : 220));
            g.fill(x, y, 4, PRESET_CARD_H, presets[i].accent);
            g.text(font, Component.literal(names[i]), x + Spacing.S3, y + (PRESET_CARD_H - font.lineHeight) / 2, presets[i].foreground);
            if (active) g.text(font, Component.literal("\u2713"), x + w - Spacing.S4, y + (PRESET_CARD_H - font.lineHeight) / 2, presets[i].accent);
        }
    }

    private void drawPreview(GuiGraphicsExtractor g) {
        int labelY = railY + PRESETS_H + Spacing.S2;
        int py = labelY + font.lineHeight + Spacing.S1;
        int px = railX, pw = railW;
        int ph = railY + railH - py - Spacing.S2;
        if (ph < 120) return;
        g.text(font, Component.literal("Preview"), px, labelY, ColorUtil.withAlpha(work.mutedForeground, 220));
        Panel.draw(g, px, py, pw, ph, ColorUtil.withAlpha(work.card, 235));
        int cx = px + Spacing.S3, cy = py + Spacing.S3;
        Panel.draw(g, cx, cy, pw - Spacing.S6, 40, ColorUtil.withAlpha(work.background, 220));
        g.text(font, Component.literal("Sample Module"), cx + Spacing.S2, cy + Spacing.S2, work.foreground);
        g.text(font, Component.literal("Subtle description text"), cx + Spacing.S2, cy + Spacing.S2 + font.lineHeight, ColorUtil.withAlpha(work.mutedForeground, 220));
        int tw = 44, th = 22, tx = px + Spacing.S3, ty = cy + 50;
        g.fillGradient(tx, ty, tx + tw, ty + th, work.accent, ColorUtil.withAlpha(work.accent, 180));
        Panel.drawHollowRect(g, tx, ty, tw, th, work.border);
        g.fill(tx + tw - 20, ty + 3, tx + tw - 4, ty + th - 3, 0xFFFFFFFF);
        int bw = 90, bh = 26, bx = tx, by = ty + th + Spacing.S2;
        boolean bhov = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        g.fill(bx, by, bx + bw, by + bh, ColorUtil.lerpARGB(work.background, work.foreground, bhov ? 0.25f : 0.12f));
        Panel.drawHollowRect(g, bx, by, bw, bh, work.border);
        g.text(font, Component.literal("Button"), bx + (bw - font.width("Button")) / 2, by + (bh - font.lineHeight) / 2, work.foreground);
    }

    private void drawSettingsList(GuiGraphicsExtractor g, float delta) {
        Panel.draw(g, listX, listY, listW, listH, ColorUtil.withAlpha(work.background, 30));
        scroll.h = listH;
        scroll.render(g, font, listX, listY, listW, mx, my, delta);

        if (colorPicker == null && mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
            Widget child = scroll.childAt(my);
            if (child instanceof Tip t && t.tip() != null && !t.tip().isEmpty()) {
                renderTooltip(g, t.tip());
            }
        }
    }

    private void renderTooltip(GuiGraphicsExtractor g, String tip) {
        int tw = font.width(tip) + 10;
        int tx = mx + 12;
        int ty = my - 22;
        if (tx + tw > width) tx = mx - tw - 12;
        if (ty < 0) ty = my + 12;
        int th = font.lineHeight + 6;
        Panel.draw(g, tx, ty, tw, th, ColorUtil.withAlpha(work.popover, 240));
        g.text(font, Component.literal(tip), tx + 5, ty + 3, work.popoverForeground);
    }

    private void drawFooter(GuiGraphicsExtractor g) {
        int bw = collapsedPreview ? 80 : 96, bh = 28, gap = collapsedPreview ? Spacing.S2 : Spacing.S3;
        int by = footerY + (FOOTER_H - bh) / 2;
        int total = bw * 3 + gap * 2;
        int startX = panelX + (panelW - total) / 2;

        boolean applyH = inButton(mx, my, startX, by, bw, bh);
        boolean exportH = inButton(mx, my, startX + bw + gap, by, bw, bh);
        boolean importH = inButton(mx, my, startX + (bw + gap) * 2, by, bw, bh);

        int applyFill = ColorUtil.withAlpha(work.accent, applyH ? 235 : 200);
        g.fill(startX, by, startX + bw, by + bh, applyFill);
        Panel.drawHollowRect(g, startX, by, bw, bh, work.accent);
        g.text(font, Component.literal("Apply"), startX + (bw - font.width("Apply")) / 2, by + (bh - font.lineHeight) / 2, 0xFFFFFFFF);

        int expFill = ColorUtil.lerpARGB(work.background, work.foreground, exportH ? 0.22f : 0.12f);
        g.fill(startX + bw + gap, by, startX + (bw + gap) * 2, by + bh, expFill);
        Panel.drawHollowRect(g, startX + bw + gap, by, bw, bh, work.border);
        g.text(font, Component.literal("Export"), startX + bw + gap + (bw - font.width("Export")) / 2, by + (bh - font.lineHeight) / 2, work.foreground);

        int impFill = ColorUtil.lerpARGB(work.background, work.foreground, importH ? 0.22f : 0.12f);
        g.fill(startX + (bw + gap) * 2, by, startX + (bw + gap) * 2 + bw, by + bh, impFill);
        Panel.drawHollowRect(g, startX + (bw + gap) * 2, by, bw, bh, work.border);
        g.text(font, Component.literal("Import"), startX + (bw + gap) * 2 + (bw - font.width("Import")) / 2, by + (bh - font.lineHeight) / 2, work.foreground);
    }

    private boolean inButton(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mxx = event.x(), myy = event.y();
        int btn = event.buttonInfo().input();
        if (btn != 0) return super.mouseClicked(event, doubleClick);

        if (colorPicker != null) {
            if (colorPicker.handleClick(mxx, myy)) return true;
            colorPicker = null;
            return true;
        }

        String[] names = {"Lunar", "Dark", "Light", "Amoled"};
        ThemeData[] presets = {ThemePresets.LUNAR, ThemePresets.DARK, ThemePresets.LIGHT, ThemePresets.AMOLED};
        for (int i = 0; i < PRESETS_COUNT; i++) {
            int x = railX, y = railY + i * (PRESET_CARD_H + Spacing.S2), w = railW;
            if (inButton((int) mxx, (int) myy, x, y, w, PRESET_CARD_H)) {
                work = presets[i].clone();
                work.preset = names[i];
                buildItems();
                return true;
            }
        }

        int bw = collapsedPreview ? 80 : 96, bh = 28, gap = collapsedPreview ? Spacing.S2 : Spacing.S3;
        int by = footerY + (FOOTER_H - bh) / 2;
        int totalW = bw * 3 + gap * 2;
        int startX = panelX + (panelW - totalW) / 2;
        if (inButton((int) mxx, (int) myy, startX, by, bw, bh)) { apply(); return true; }
        if (inButton((int) mxx, (int) myy, startX + bw + gap, by, bw, bh)) { exportTheme(); return true; }
        if (inButton((int) mxx, (int) myy, startX + (bw + gap) * 2, by, bw, bh)) { importTheme(); return true; }

        if (mxx >= listX && mxx <= listX + listW && myy >= listY && myy <= listY + listH) {
            if (scroll.mouseClicked(mxx, myy, btn)) return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double mxx = event.x(), myy = event.y();
        if (colorPicker != null) { colorPicker.handleClick(mxx, myy); return true; }
        if (mxx >= listX && mxx <= listX + listW && myy >= listY && myy <= listY + listH) {
            if (scroll.mouseDragged(mxx, myy)) return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            scroll.mouseScrolled(deltaY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(event);
    }

    private void apply() { Theme.apply(work); flash("Theme applied"); }
    private void exportTheme() { Theme.apply(work); flash("Saved to crest-theme.json"); }
    private void importTheme() { Theme.load(); work = Theme.get().clone(); buildItems(); flash("Loaded crest-theme.json"); }
    private void flash(String m) { message = m; messageTimer = 120; }

    @Override
    public void onClose() { anim.closeTo(parent); }
    @Override
    public boolean isPauseScreen() { return false; }

    private class ThemeHeader implements Widget, Tip {
        private final String text;
        private final boolean collapsible;
        private final boolean expanded;
        private final String tooltip;

        ThemeHeader(String t, boolean collapsible, boolean expanded, String tooltip) {
            this.text = t; this.collapsible = collapsible; this.expanded = expanded; this.tooltip = tooltip;
        }

        @Override
        public int getWidth() { return 0; }

        @Override
        public int getHeight() { return Spacing.S8; }

        @Override
        public void render(GuiGraphicsExtractor g, Font f, int x, int y, int w, int mx, int my, float delta) {
            int ay = y + Spacing.S2;
            g.fill(x, ay, x + 3, ay + f.lineHeight + 2, ColorUtil.withAlpha(work.accent, 220));
            if (collapsible) {
                String arrow = expanded ? "\u25BC" : "\u25B6";
                g.text(f, Component.literal(arrow), x + Spacing.S2, ay, ColorUtil.withAlpha(work.mutedForeground, 200));
                g.text(f, Component.literal(text), x + Spacing.S3 + f.width(arrow), ay, work.foreground);
            } else {
                g.text(f, Component.literal(text), x + Spacing.S2, ay, work.foreground);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && collapsible) {
                sectionExpanded.put(text, !Boolean.TRUE.equals(sectionExpanded.get(text)));
                buildItems();
                return true;
            }
            return false;
        }

        @Override
        public String tip() { return tooltip; }
    }

    private class ThemeColorItem implements Widget, Tip {
        private final String name;
        private final String tooltip;
        private final Supplier<Integer> get;
        private final Consumer<Integer> set;
        private int lastX, lastY, lastW;

        ThemeColorItem(String n, String tip, Supplier<Integer> g, Consumer<Integer> s) {
            this.name = n; this.tooltip = tip; this.get = g; this.set = s;
        }

        @Override
        public int getWidth() { return 0; }

        @Override
        public int getHeight() { return Theme.ROW_H(); }

        @Override
        public String tip() { return tooltip; }

        @Override
        public void render(GuiGraphicsExtractor g, Font f, int x, int y, int w, int mx, int my, float delta) {
            lastX = x; lastY = y; lastW = w;
            int rh = Theme.ROW_H();
            if (mx >= x && mx <= x + w && my >= y && my <= y + rh) {
                g.fill(x, y, x + w, y + rh, ColorUtil.withAlpha(work.accent, 8));
            }
            g.text(f, Component.literal(name), x + Spacing.S1, y + (rh - f.lineHeight) / 2, work.foreground);
            int sw = 22, sx = x + w - sw - 4, sy = y + (rh - sw) / 2;
            g.fill(sx - 1, sy - 1, sx + sw + 1, sy + sw + 1, 0x44000000);
            g.fill(sx, sy, sx + sw, sy + sw, 0xFF000000 | get.get());
            if (mx >= sx && mx <= sx + sw && my >= sy && my <= sy + sw) {
                g.fill(sx - 1, sy - 1, sx + sw + 1, sy + sw + 1, work.accent);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int rh = Theme.ROW_H();
            int sw = 22, sx = lastX + lastW - sw - 4, sy = lastY + (rh - sw) / 2;
            if (mx >= sx && mx <= sx + sw && my >= sy && my <= sy + sw) {
                colorPicker = new ColorPicker().setValue(get.get())
                    .setOnPick(c -> set.accept(c));
                return true;
            }
            return false;
        }
    }

    private class ThemeSliderItem implements Widget, Tip {
        private final String name;
        private final String tooltip;
        private final int min, max;
        private final Supplier<Integer> get;
        private final Consumer<Integer> set;
        private final Function<Integer, String> fmt;
        private boolean dragging;
        private int lastX, lastY, lastW;

        ThemeSliderItem(String n, String tip, int mn, int mx2, Supplier<Integer> g, Consumer<Integer> s) {
            this(n, tip, mn, mx2, g, s, v -> String.valueOf(v));
        }

        ThemeSliderItem(String n, String tip, int mn, int mx2, Supplier<Integer> g, Consumer<Integer> s, Function<Integer, String> fmt) {
            this.name = n; this.tooltip = tip; this.min = mn; this.max = mx2; this.get = g; this.set = s; this.fmt = fmt;
        }

        @Override
        public int getWidth() { return 0; }

        @Override
        public int getHeight() { return Theme.ROW_H(); }

        @Override
        public String tip() { return tooltip; }

        @Override
        public void render(GuiGraphicsExtractor g, Font f, int x, int y, int w, int mx, int my, float delta) {
            lastX = x; lastY = y; lastW = w;
            int rh = Theme.ROW_H();
            if (mx >= x && mx <= x + w && my >= y && my <= y + rh) {
                g.fill(x, y, x + w, y + rh, ColorUtil.withAlpha(work.accent, 8));
            }
            int valW = 46;
            int trackX = x, trackW = w - valW - Spacing.S2, trackY = y + rh - 14, trackH = 6;
            g.text(f, Component.literal(name), x + Spacing.S1, y + 4, work.foreground);
            g.text(f, Component.literal(fmt.apply(get.get())), x + w - valW, y + 4, ColorUtil.withAlpha(work.mutedForeground, 220));
            g.fill(trackX, trackY, trackX + trackW, trackY + trackH, ColorUtil.withAlpha(work.border, 200));
            float t = (get.get() - min) / (float) (max - min);
            int kw = 10;
            int kx = trackX + (int) (t * (trackW - kw));
            g.fill(kx, trackY - 2, kx + kw, trackY + trackH + 2, work.accent);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int rh = Theme.ROW_H();
            int valW = 46;
            int trackX = lastX, trackW = lastW - valW - Spacing.S2, trackY = lastY + rh - 14, trackH = 6;
            if (mx >= trackX && mx <= trackX + trackW && my >= trackY - 5 && my <= trackY + trackH + 5) {
                dragging = true; update((int) mx, trackX, trackW); return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double mx, double my) {
            if (!dragging) return false;
            int valW = 46;
            update((int) mx, lastX, lastW - valW - Spacing.S2); return true;
        }

        private void update(int mxp, int trackX, int trackW) {
            float t = Anim.clamp((float) (mxp - trackX) / trackW, 0, 1);
            set.accept(min + Math.round(t * (max - min)));
        }
    }

    private class ThemeModeItem implements Widget, Tip {
        private final String name;
        private final String tooltip;
        private final String[] opts;
        private final Supplier<Integer> get;
        private final Consumer<Integer> set;
        private int lastX, lastY, lastW;

        ThemeModeItem(String n, String tip, String[] o, Supplier<Integer> g, Consumer<Integer> s) {
            this.name = n; this.tooltip = tip; this.opts = o; this.get = g; this.set = s;
        }

        @Override
        public int getWidth() { return 0; }

        @Override
        public int getHeight() { return Theme.ROW_H(); }

        @Override
        public String tip() { return tooltip; }

        @Override
        public void render(GuiGraphicsExtractor g, Font f, int x, int y, int w, int mx, int my, float delta) {
            lastX = x; lastY = y; lastW = w;
            int rh = Theme.ROW_H();
            if (mx >= x && mx <= x + w && my >= y && my <= y + rh) {
                g.fill(x, y, x + w, y + rh, ColorUtil.withAlpha(work.accent, 8));
            }
            g.text(f, Component.literal(name), x + Spacing.S1, y + (rh - f.lineHeight) / 2, work.foreground);
            int sel = (int) Anim.clamp(get.get(), 0, opts.length - 1);
            int lw = f.width(opts[sel]) + Spacing.S4;
            int lx = x + w - lw;
            g.fill(lx, y + (rh - 22) / 2, lx + lw, y + (rh + 22) / 2, ColorUtil.withAlpha(work.border, 120));
            g.text(font, Component.literal(opts[sel]), lx + Spacing.S2, y + (rh - f.lineHeight) / 2, work.accent);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int rh = Theme.ROW_H();
            int sel = (int) Anim.clamp(get.get(), 0, opts.length - 1);
            int lw = font.width(opts[sel]) + Spacing.S4;
            int lx = lastX + lastW - lw;
            if (mx >= lx && mx <= lx + lw && my >= lastY && my <= lastY + rh) {
                set.accept((get.get() + 1) % opts.length);
                work.preset = "Custom";
                return true;
            }
            return false;
        }
    }

    private class ThemeToggleItem implements Widget, Tip {
        private final String name;
        private final String tooltip;
        private final Supplier<Boolean> get;
        private final Consumer<Boolean> set;
        private int lastX, lastY, lastW;

        ThemeToggleItem(String n, String tip, Supplier<Boolean> g, Consumer<Boolean> s) {
            this.name = n; this.tooltip = tip; this.get = g; this.set = s;
        }

        @Override
        public int getWidth() { return 0; }

        @Override
        public int getHeight() { return Theme.ROW_H(); }

        @Override
        public String tip() { return tooltip; }

        @Override
        public void render(GuiGraphicsExtractor g, Font f, int x, int y, int w, int mx, int my, float delta) {
            lastX = x; lastY = y; lastW = w;
            int rh = Theme.ROW_H();
            if (mx >= x && mx <= x + w && my >= y && my <= y + rh) {
                g.fill(x, y, x + w, y + rh, ColorUtil.withAlpha(work.accent, 8));
            }
            g.text(f, Component.literal(name), x + Spacing.S1, y + (rh - f.lineHeight) / 2, work.foreground);
            boolean on = get.get();
            int tw = 36, th = 20;
            int tx = x + w - tw - Spacing.S2;
            int ty = y + (rh - th) / 2;
            int trackColor = on ? ColorUtil.withAlpha(work.accent, 200) : ColorUtil.withAlpha(work.border, 150);
            g.fill(tx, ty, tx + tw, ty + th, trackColor);
            Panel.drawHollowRect(g, tx, ty, tw, th, ColorUtil.withAlpha(work.border, 100));
            int knobX = on ? tx + tw - 18 : tx + 2;
            g.fill(knobX, ty + 2, knobX + 16, ty + th - 2, on ? 0xFFFFFFFF : ColorUtil.withAlpha(work.mutedForeground, 200));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int rh = Theme.ROW_H();
            int tw = 36, th = 20;
            int tx = lastX + lastW - tw - Spacing.S2;
            int ty = lastY + (rh - th) / 2;
            if (mx >= tx && mx <= tx + tw && my >= lastY && my <= lastY + rh) {
                set.accept(!get.get());
                work.preset = "Custom";
                return true;
            }
            return false;
        }
    }
}
