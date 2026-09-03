package com.crest.client.core;

import com.crest.client.core.setting.*;
import com.crest.client.ui.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class ModuleDetailScreen extends Screen {
    private static final int HEADER_H = 48;
    private static final int ROW_H = 32;
    private static final int GROUP_HEADER_H = 28;
    private static final int PANEL_MIN_W = 280;
    private static final int PANEL_MAX_W = 480;

    private final CrestModule module;
    private final Screen parent;
    private final List<Setting<?>> settings;
    private final List<SettingGroup> groups;

    private int panelX, panelY, panelW, panelH;
    private Breakpoints.Size currentSize = Breakpoints.Size.MD;

    private final Map<Setting<?>, Widget> widgetCache = new HashMap<>();
    private final Map<String, Animated> toggleAnim = new HashMap<>();
    private Widget activeWidget;
    private final ScrollContainer scroll = new ScrollContainer();

    private boolean keybindCapture;
    private com.crest.client.core.setting.KeybindSetting keybindCaptureSetting;

    private ColorPicker colorPicker;
    private final Animated colorPickerAnim = new Animated(0f, 12f);

    private final ScreenAnim anim = new ScreenAnim();
    private final SearchBar searchBar = new SearchBar(q -> {}, "Search settings...");
    private int hoveredRow = -1;

    private final Theme.ThemeChangeListener themeListener = () -> { if (minecraft != null && minecraft.screen == this) init(); };

    public ModuleDetailScreen(CrestModule module, Screen parent) {
        super(Component.literal(module.getName()));
        this.module = module;
        this.parent = parent;
        this.settings = module.getSettings();
        this.groups = module.getSettingGroups();
        anim.begin();
    }

    @Override
    protected void init() {
        currentSize = Breakpoints.getCurrentSize(width);
        computePanelSize();
        Theme.addListener(themeListener);
    }

    @Override
    public void removed() {
        super.removed();
        Theme.removeListener(themeListener);
    }

    private void computePanelSize() {
        Breakpoints.Size size = Breakpoints.getCurrentSize(width);
        if (size == Breakpoints.Size.XS || size == Breakpoints.Size.SM) {
            panelW = Math.max(PANEL_MIN_W, Math.min(width - Spacing.S3 * 2, width));
        } else {
            panelW = Math.max(PANEL_MIN_W, Math.min(width - Spacing.S10, PANEL_MAX_W));
        }
        panelX = (width - panelW) / 2;
        panelY = HEADER_H + Spacing.S2;
        panelH = height - panelY - Spacing.S2;
    }

    private Animated toggleFor(String id) {
        return toggleAnim.computeIfAbsent(id, k -> new Animated(0f, 12f));
    }

    private Widget getWidget(Setting<?> setting) {
        return widgetCache.computeIfAbsent(setting, s -> {
            Widget w;
            if (s instanceof BooleanSetting bs) w = new ToggleRow(bs);
            else if (s instanceof IntegerSetting || s instanceof FloatSetting) w = new SliderRow(s);
            else if (s instanceof StringSetting ss) w = new TextRow(ss);
            else if (s instanceof ModeSetting ms) w = new ModeRow(ms);
            else if (s instanceof ColorSetting cs) {
                ColorRow cr = new ColorRow(cs);
                cr.setOnPick(cs2 -> colorPicker = new ColorPicker().setValue(cs2.get()).setOnPick(rgb -> { cs2.set(rgb); CrestModules.getConfigManager().markDirty(); }));
                w = cr;
            } else if (s instanceof KeybindSetting ks) {
                KeybindRow kr = new KeybindRow(ks);
                kr.setOnCapture((modId, ks2) -> { keybindCapture = true; keybindCaptureSetting = ks2; });
                w = kr;
            } else w = null;
            return w;
        });
    }

    private List<Widget> getVisibleWidgets() {
        String q = searchBar.getText();
        List<Widget> list = new ArrayList<>();
        
        if (!groups.isEmpty()) {
            for (SettingGroup group : groups) {
                boolean groupMatches = SearchBar.fuzzyMatch(q, group.getName());
                boolean anyChildMatches = false;
                
                for (Setting<?> s : group.getSettings()) {
                    if (!s.isVisible()) continue;
                    if (!q.isEmpty() && !groupMatches && !SearchBar.fuzzyMatch(q, s.getName()) && !SearchBar.fuzzyMatch(q, s.getDescription())) continue;
                    anyChildMatches = true;
                    break;
                }
                
                if (!anyChildMatches && !q.isEmpty() && !groupMatches) continue;
                
                GroupHeaderRow header = new GroupHeaderRow(group);
                list.add(header);
                
                if (group.isExpanded() || !q.isEmpty()) {
                    for (Setting<?> s : group.getSettings()) {
                        if (!s.isVisible()) continue;
                        if (!q.isEmpty() && !groupMatches && !SearchBar.fuzzyMatch(q, s.getName()) && !SearchBar.fuzzyMatch(q, s.getDescription())) continue;
                        list.add(getWidget(s));
                    }
                }
            }
        } else {
            for (Setting<?> s : settings) {
                if (!s.isVisible()) continue;
                if (!q.isEmpty() && !SearchBar.fuzzyMatch(q, s.getName()) && !SearchBar.fuzzyMatch(q, s.getDescription())) continue;
                list.add(getWidget(s));
            }
        }
        return list;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Theme.tick(delta);
        anim.update(delta);
        colorPickerAnim.tick(delta);

        Breakpoints.Size newSize = Breakpoints.getCurrentSize(width);
        if (newSize != currentSize) {
            currentSize = newSize;
            computePanelSize();
        }

        anim.backdrop(g, width, height);

        int wy = anim.slideY(24);
        g.pose().pushMatrix();
        g.pose().translate(0, wy);

        renderHeader(g, mx, my);
        renderBody(g, mx, my, delta);

        if (colorPicker != null) {
            colorPickerAnim.set(1f);
            renderColorPicker(g, mx, my);
        } else {
            colorPickerAnim.set(0f);
        }

        g.pose().popMatrix();
    }

    private void renderHeader(GuiGraphicsExtractor g, int mx, int my) {
        boolean enabled = CrestModules.isEnabled(module.getId());

        g.fillGradient(0, 0, width, HEADER_H, Theme.GLASS_BG, ColorUtil.withAlpha(Theme.GLASS_BG, 110));
        int accent = Theme.getAnimatedAccent();

        String back = "\u2190";
        int backW = font.width(back) + Spacing.S2;
        boolean backHover = mx >= Spacing.S3 && mx <= Spacing.S3 + backW && my >= Spacing.S2 && my <= HEADER_H - Spacing.S2;
        if (backHover) {
            g.fill(Spacing.S3, Spacing.S2, Spacing.S3 + backW, HEADER_H - Spacing.S2, ColorUtil.withAlpha(accent, 20));
        }
        g.text(font, Component.literal(back), Spacing.S3, (HEADER_H - font.lineHeight) / 2,
            backHover ? accent : Theme.FOREGROUND);

        int nameX = Spacing.S3 + backW + Spacing.S3;
        g.text(font, Component.literal(module.getName()), nameX, (HEADER_H - font.lineHeight) / 2, Theme.FOREGROUND);

        String desc = module.getDescription();
        if (!desc.isEmpty()) {
            int descMaxW = Math.max(panelW - nameX - 100, 50);
            String truncated = font.width(desc) > descMaxW
                ? font.plainSubstrByWidth(desc, descMaxW - 4) + "\u2026"
                : desc;
            g.text(font, Component.literal(truncated), nameX, (HEADER_H - font.lineHeight) / 2 + font.lineHeight + 1,
                ColorUtil.withAlpha(Theme.MUTED_FOREGROUND, 180));
        }

        int count = settings.size();
        String countStr = count + " setting" + (count != 1 ? "s" : "");
        if (currentSize != Breakpoints.Size.XS) {
            int countW = font.width(countStr);
            int countX = width - Spacing.S3 - countW - ToggleSwitch.W - Spacing.S3;
            g.text(font, Component.literal(countStr), countX, HEADER_H - font.lineHeight - Spacing.S1,
                ColorUtil.withAlpha(Theme.MUTED_FOREGROUND, 100));
        }

        int toggleX = width - Spacing.S3 - ToggleSwitch.W;
        ToggleSwitch.render(g, toggleX, (HEADER_H - ToggleSwitch.H) / 2, enabled, enabled ? 1f : 0f);
    }

    private void renderBody(GuiGraphicsExtractor g, int mx, int my, float delta) {
        List<Widget> widgets = getVisibleWidgets();
        int searchH = 36;
        
        int contentH = 0;
        for (Widget w : widgets) {
            contentH += w.getHeight();
        }
        contentH += Spacing.S4;
        
        int totalH = searchH + Spacing.S2 + contentH;

        int bh = Math.min(panelH, totalH + Spacing.S2);
        Panel.drawElevated(g, panelX, panelY, panelW, bh, ColorUtil.withAlpha(Theme.CARD, 200), Theme.ELEVATION_1);
        g.fill(panelX + 2, panelY, panelX + panelW - 2, panelY + 1, ColorUtil.withAlpha(Theme.getAnimatedAccent(), 80));

        int sx = panelX + Spacing.S3;
        int sw = panelW - Spacing.S3 * 2;
        int searchY = panelY + Spacing.S2 + 4;
        searchBar.render(g, font, sx, searchY, sw, mx, my, delta);

        int scrollY = searchY + searchH + 4;
        int scrollH = bh - (scrollY - panelY) - Spacing.S2;

        scroll.rowHeight(ROW_H).children(widgets);
        scroll.hoverColor = ColorUtil.withAlpha(Theme.MUTED, 100);

        int cumulativeY = 0;
        for (int i = 1; i < widgets.size(); i++) {
            Widget prev = widgets.get(i - 1);
            cumulativeY += prev.getHeight();
            int dy = scrollY + cumulativeY - (int) scroll.scrollOffset;
            if (dy >= scrollY && dy <= scrollY + scrollH) {
                if (!(widgets.get(i) instanceof GroupHeaderRow)) {
                    g.fill(sx, dy - 1, sx + sw, dy, ColorUtil.withAlpha(Theme.BORDER_LIGHT, 40));
                }
            }
        }

        scroll.render(g, font, sx, scrollY, sw, mx, my, delta);

        Setting<?> hoveredSetting = null;
        hoveredRow = -1;
        int yOffset = 0;
        int idx = 0;
        
        for (Widget w : widgets) {
            int cy = scrollY + yOffset - (int) scroll.scrollOffset;
            int ch = w.getHeight();
            
            if (mx >= sx && mx <= sx + sw && my >= cy && my <= cy + ch) {
                hoveredRow = idx;
                if (w instanceof ToggleRow || w instanceof SliderRow || w instanceof TextRow || 
                    w instanceof ModeRow || w instanceof ColorRow || w instanceof KeybindRow) {
                    for (Setting<?> s : settings) {
                        if (getWidget(s) == w) {
                            hoveredSetting = s;
                            break;
                        }
                    }
                    if (hoveredSetting == null && !groups.isEmpty()) {
                        for (SettingGroup group : groups) {
                            for (Setting<?> s : group.getSettings()) {
                                if (getWidget(s) == w) {
                                    hoveredSetting = s;
                                    break;
                                }
                            }
                            if (hoveredSetting != null) break;
                        }
                    }
                }
                break;
            }
            yOffset += ch;
            idx++;
        }
        
        if (hoveredSetting != null) {
            String sdesc = hoveredSetting.getDescription();
            if (sdesc != null && !sdesc.isEmpty()) {
                int tx = mx + 12;
                int ty = my - 20;
                if (tx + 150 > width) tx = mx - 160;
                if (ty < 0) ty = my + 12;
                int tw = font.width(sdesc) + 8;
                int th = font.lineHeight + 4;
                Panel.draw(g, tx, ty, tw, th, ColorUtil.withAlpha(Theme.POPOVER, 230));
                g.text(font, Component.literal(sdesc), tx + 4, ty + 2, Theme.POPOVER_FOREGROUND);
            }
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int cp = event.codepoint();
        if (searchBar.charTyped(cp, 0)) return true;
        if (activeWidget != null && activeWidget.charTyped(cp, 0)) return true;
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (searchBar.keyPressed(key, 0, 0)) return true;

        if (keybindCapture) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { keybindCapture = false; keybindCaptureSetting = null; }
            else if (keybindCaptureSetting != null) {
                keybindCaptureSetting.set(key);
                CrestModules.getConfigManager().markDirty();
                KeybindManager.markDirty();
                keybindCapture = false; keybindCaptureSetting = null;
            }
            return true;
        }

        if (activeWidget != null && activeWidget.keyPressed(key, 0, 0)) return true;

        if (colorPicker != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { closeColorPicker(); return true; }
            return super.keyPressed(event);
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int btn = event.buttonInfo().input();

        if (btn == 0 && colorPicker != null) {
            if (handleColorPickerClick(mx, my)) return true;
            return super.mouseClicked(event, doubleClick);
        }

        String back = "\u2190";
        int backW = font.width(back) + Spacing.S2;
        if (btn == 0 && mx >= Spacing.S3 && mx <= Spacing.S3 + backW && my >= Spacing.S2 && my <= HEADER_H - Spacing.S2) {
            onClose();
            return true;
        }

        if (btn == 0) {
            int toggleX = width - Spacing.S3 - ToggleSwitch.W;
            int toggleY = (HEADER_H - ToggleSwitch.H) / 2;
            if (mx >= toggleX && mx <= toggleX + ToggleSwitch.W && my >= toggleY && my <= toggleY + ToggleSwitch.H) {
                CrestModules.setEnabled(module.getId(), !CrestModules.isEnabled(module.getId()));
                return true;
            }
        }

        int searchH = 36;
        int searchY = panelY + Spacing.S2 + 4;
        if (mx >= panelX + Spacing.S3 && mx <= panelX + panelW - Spacing.S3 && my >= searchY && my <= searchY + searchH) {
            searchBar.mouseClicked(mx, my, btn);
            return true;
        }

        if (scroll.mouseClicked(mx, my, btn)) {
            Widget child = scroll.childAt(my);
            if (child != null) {
                activeWidget = child;
                if (child instanceof KeybindRow kr) {
                    kr.setCapturing(keybindCapture && keybindCaptureSetting == kr.getSetting());
                }
            }
            return true;
        }

        if (activeWidget instanceof TextRow && btn == 0) activeWidget = null;

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double mx = event.x(), my = event.y();
        if (colorPicker != null) { handleColorPickerClick(mx, my); return true; }

        if (activeWidget instanceof SliderRow sr) {
            sr.mouseDragged(mx, my);
            return true;
        }

        if (scroll.mouseDragged(mx, my)) return true;

        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scroll.mouseScrolled(deltaY);
        return true;
    }

    @Override
    public void onClose() {
        anim.closeTo(parent);
    }

    private void renderColorPicker(GuiGraphicsExtractor g, int mx, int my) {
        int pw = Breakpoints.isXsOrSmaller(width) ? Math.min(200, width - Spacing.S2 * 2) : 220;
        int ph = 260;
        int px = Math.max(Spacing.S2, Math.min(width / 2 - pw / 2, width - pw - Spacing.S2));
        int py = HEADER_H + Spacing.S3;
        int a = (int) (255 * colorPickerAnim.get());
        int accent = Theme.getAnimatedAccent();

        Panel.drawElevated(g, px, py, pw, ph, ColorUtil.withAlpha(Theme.CARD, a), Theme.ELEVATION_3);
        g.fill(px + 2, py, px + pw - 2, py + 1, ColorUtil.withAlpha(accent, Theme.topStripAlpha));

        colorPicker.layout(px, py, pw, ph);
        colorPicker.render(g, font, mx, my, a);
    }

    private boolean handleColorPickerClick(double mx, double my) {
        if (colorPicker.handleClick(mx, my)) {
            CrestModules.getConfigManager().markDirty();
            return true;
        }
        return false;
    }

    private void closeColorPicker() { colorPicker = null; CrestModules.getConfigManager().save(); }

    @Override
    public boolean isPauseScreen() { return false; }
}
