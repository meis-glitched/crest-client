package com.crest.client.core;

import com.crest.client.music.MusicMiniPlayer;
import com.crest.client.music.MusicModule;
import com.crest.client.music.MusicScreen;
import com.crest.client.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

public class CrestMenu extends Screen implements TextCapturingScreen {
    private enum Tab { HOME, MODS, SETTINGS }

    private static final int MARGIN = 20;
    private static final int TOPBAR_H = 56;
    private static final int MINI_H = 80;
    private static final int MINI_M = 12;
    private static final int SEARCH_H = 36;
    private static final int CHIP_H = 28;
    private static final int MODULE_CARD_H = 80;
    private static final int MODULE_CARD_GAP = 6;
    private static final int SECTION_HEADER_H = 32;
    private static final int SECTION_GAP = 10;
    private static final int GRID_COLS = 3;
    private static final int RAIL_W = 200;
    private static final int TOGGLE_W = 44;
    private static final int TOGGLE_H = 24;

    private static final String FAV_CAT = "Favorites";

    private final Set<String> expandedCategories = new HashSet<>();

    private Tab tab = Tab.HOME;
    private int mx, my;
    private int pX, pY, pW, pH;
    private int contentX, contentY, contentW, contentH;
    private int miniX, miniY, miniW, miniH;

    // --- Mods tab state ---
    private final SearchBar searchBar = new SearchBar(q -> {
        scrollTarget = 0;
        scrollOffset = 0;
    }, "Search modules...");
    private String category = null;
    private int filterMode = 0; // 0 all, 1 enabled, 2 disabled
    private boolean sortAsc = true;
    private final DropdownMenu filterMenu = new DropdownMenu();
    private int filterBtnX, filterBtnY, gearBtnX;
    private float chipScroll = 0;
    private int chipMaxScroll = 0;
    private float scrollOffset = 0;
    private float scrollTarget = 0;
    private int maxScroll = 0;
    private int hoveredSection = -1;
    private int hoveredModInSection = -1;
    private int lastHoveredSection = -1;
    private int lastHoveredModIdx = -1;
    private int lastHoveredChip = -1;
    private boolean lastFilterHover;
    private boolean lastGearHover;
    private final Map<String, Animated> toggleAnims = new HashMap<>();
    private final Map<String, Animated> rowHoverAnims = new HashMap<>();
    private int cascadeCardIndex;
    private int cascadeCardTotal;
    private boolean draggingScrollbar;
    private int scrollbarDragStartY;
    private float scrollbarDragStartOffset;

    // --- Home tab state ---
    private List<ServerData> servers = new ArrayList<>();
    private int hoveredServer = -1;
    private int lastHoveredServer = -1;
    private int playX, playY, playW, playH;
    private int serverListY, serverCardH, serverCardGap, serverCardW;

    // --- Settings tab state ---
    private final String[] settingsEntries = {"HUD Layout", "Theme", "Animations", "Profiles", "Music", "Resource Packs", "Streamer"};
    private int hoveredEntry = -1;
    private int lastHoveredEntry = -1;
    private int blurX, blurY, blurW;
    private int accentX, accentY;
    private int themeBtnX, themeBtnY, themeBtnW, themeBtnH;
    private int hoveredThemeBtn = -1;
    private int lastHoveredThemeBtn = -1;

    private final ScreenAnim anim = new ScreenAnim();
    private boolean poppedFirstTime;
    private final Animated[] tabAnims = new Animated[Tab.values().length];
    private final QuickSettingsDrawer quickSettings = new QuickSettingsDrawer();
    private final MusicMiniPlayer miniPlayer = new MusicMiniPlayer();
    private Breakpoints.Size currentSize = Breakpoints.Size.MD;

    protected CrestMenu() {
        super(Component.literal(""));
        for (int i = 0; i < tabAnims.length; i++) tabAnims[i] = new Animated(0f, 14f);
    }

    @Override
    protected void init() {
        Theme.load();
        anim.updateConfig();
        if (!poppedFirstTime) {
            poppedFirstTime = true;
            anim.begin();
        }
        for (Animated a : tabAnims) a.set(0f);
        rebuildFilterMenu();
        loadServers();
        computeLayout();
    }

    private void loadServers() {
        servers = new ArrayList<>();
        try {
            ServerList list = new ServerList(minecraft);
            list.load();
            for (int i = 0; i < list.size(); i++) servers.add(list.get(i));
        } catch (Exception ignored) {}
    }

    private void rebuildFilterMenu() {
        filterMenu.items.clear();
        filterMenu.addChecked("All modules", filterMode == 0, () -> setFilter(0));
        filterMenu.addChecked("Enabled only", filterMode == 1, () -> setFilter(1));
        filterMenu.addChecked("Disabled only", filterMode == 2, () -> setFilter(2));
        filterMenu.addSeparator();
        filterMenu.addChecked("Sort: A-Z", sortAsc, () -> setSort(true));
        filterMenu.addChecked("Sort: Z-A", !sortAsc, () -> setSort(false));
    }

    private void setFilter(int mode) {
        filterMode = mode;
        rebuildFilterMenu();
        scrollTarget = scrollOffset = 0;
    }

    private void setSort(boolean asc) {
        sortAsc = asc;
        rebuildFilterMenu();
        scrollTarget = scrollOffset = 0;
    }

    private void computeLayout() {
        Breakpoints.Size newSize = Breakpoints.getCurrentSize(width);
        if (newSize != currentSize) currentSize = newSize;

        pX = MARGIN;
        pY = MARGIN;
        pW = width - MARGIN * 2;
        pH = height - MARGIN * 2;

        contentX = pX + 16;
        contentW = pW - 32;
        contentY = pY + TOPBAR_H + 4;
        miniX = contentX;
        miniW = contentW;
        miniH = MINI_H;
        miniY = pY + pH - MINI_H - MINI_M;
        contentH = pH - (contentY - pY) - 8 - MINI_H - MINI_M;
    }

    private int modsGridY() {
        return contentY + SEARCH_H + CHIP_H + 16;
    }

    private int modsGridH() {
        return contentY + contentH - modsGridY();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        this.mx = mx;
        this.my = my;
        Theme.tick(delta);
        float open = anim.update(delta);
        searchBar.tick(delta);
        for (Animated a : tabAnims) a.tick(delta);

        if (open < 0.01f) return;

        if (Breakpoints.getCurrentSize(width) != currentSize) computeLayout();

        anim.backdrop(g, width, height);

        float tv = Anim.clamp(anim.value(), 0f, 1.15f);
        float scale = 0.9f + 0.1f * tv;
        float cx = pX + pW / 2f;
        float cy = pY + pH / 2f;

        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.pose().translate(-cx, -cy);
        g.pose().translate(0, (1 - tv) * -10);

        Panel.draw(g, pX, pY, pW, pH, Theme.GLASS_BG);
        Panel.drawHollowRect(g, pX, pY, pW, pH, Theme.BORDER_LIGHT);

        renderTopBar(g, delta);

        switch (tab) {
            case HOME -> renderHome(g, delta);
            case MODS -> renderMods(g, delta);
            case SETTINGS -> renderSettings(g, delta);
        }

        quickSettings.render(g, font, pX, pY, pW, mx, my, delta);

        miniPlayer.render(g, font, miniX, miniY, miniW, miniH, mx, my, delta);

        g.pose().popMatrix();
    }

    /** True while the menu is mid pop-in or animating out; interactions are swallowed. */
    private boolean inputBlocked() {
        return anim.isBusy() || anim.isClosing();
    }

    // ------------------------------------------------------------------ top bar

    private void renderTopBar(GuiGraphicsExtractor g, float delta) {
        int accent = Theme.getAnimatedAccent();
        int ty = pY + (TOPBAR_H - 9) / 2;

        String crest = "Crest";
        g.text(font, Component.literal(crest), pX + 16, ty, Theme.FOREGROUND);
        int crestW = font.width(crest);
        g.text(font, Component.literal("."), pX + 16 + crestW, ty, accent);

        int gap = 4;
        int tabW = 72;
        int tabsW = Tab.values().length * tabW + (Tab.values().length - 1) * gap;
        int startX = pX + pW - 16 - tabsW;
        int tabY = pY + (TOPBAR_H - 26) / 2;

        for (int i = 0; i < Tab.values().length; i++) {
            int x = startX + i * (tabW + gap);
            boolean hover = mx >= x && mx <= x + tabW && my >= tabY && my <= tabY + 26;
            boolean active = tab.ordinal() == i;
            Animated a = tabAnims[i];
            a.set(active || hover ? 1f : 0f);
            float t = a.get();

            int labelColor = active ? accent : (hover ? Theme.FOREGROUND : Theme.MUTED_FOREGROUND);
            g.text(font, Component.literal(Tab.values()[i].name().charAt(0) + Tab.values()[i].name().substring(1).toLowerCase()),
                x + (tabW - font.width(Tab.values()[i].name())) / 2, tabY + 8, labelColor);

            if (t > 0.01f) {
                int uw = (int) (28 * t);
                g.fill(x + (tabW - uw) / 2, tabY + 22, x + (tabW + uw) / 2, tabY + 24,
                    ColorUtil.lerpARGB(Theme.MUTED_FOREGROUND, accent, t));
            }
        }
    }

    // ------------------------------------------------------------------ home

    private void renderHome(GuiGraphicsExtractor g, float delta) {
        int accent = Theme.getAnimatedAccent();

        playW = 260;
        playH = 52;
        playX = contentX + (contentW - playW) / 2;
        playY = contentY + 36;

        boolean playHover = mx >= playX && mx <= playX + playW && my >= playY && my <= playY + playH;
        Panel.drawElevated(g, playX, playY, playW, playH,
            ColorUtil.withAlpha(accent, playHover ? 235 : 200), Theme.ELEVATION_2);
        Panel.drawHollowRect(g, playX, playY, playW, playH, accent);
        g.text(font, Component.literal("\u25B6"), playX + 24, playY + (playH - 9) / 2, 0xFFFFFFFF);
        g.text(font, Component.literal("Play"), playX + playW / 2 - 14, playY + (playH - 9) / 2, 0xFFFFFFFF);

        String sub = servers.isEmpty()
            ? "No servers yet — Play opens the multiplayer screen"
            : "Connects to " + servers.get(0).name + " (" + servers.get(0).ip + ")";
        g.text(font, Component.literal(sub), contentX + (contentW - font.width(sub)) / 2, playY + playH + 10,
            ColorUtil.withAlpha(Theme.MUTED_FOREGROUND, 180));

        int linkW = 120, linkH = 34, linkY = playY + playH + 34;
        int linkX0 = contentX + contentW / 2 - linkW - 6;
        int linkX1 = contentX + contentW / 2 + 6;
        boolean modsHover = mx >= linkX0 && mx <= linkX0 + linkW && my >= linkY && my <= linkY + linkH;
        boolean settHover = mx >= linkX1 && mx <= linkX1 + linkW && my >= linkY && my <= linkY + linkH;
        drawQuickLink(g, "Manage Mods", linkX0, linkY, linkW, linkH, modsHover, accent);
        drawQuickLink(g, "Settings", linkX1, linkY, linkW, linkH, settHover, accent);

        int statsY = linkY + linkH + 20;
        int enabled = (int) CrestModules.getAll().values().stream()
            .filter(m -> CrestModules.isEnabled(m.getId())).count();
        String stats = CrestModules.getAll().size() + " modules  \u2022  " + enabled + " enabled";
        g.text(font, Component.literal(stats), contentX + (contentW - font.width(stats)) / 2, statsY,
            ColorUtil.withAlpha(Theme.MUTED_FOREGROUND, 160));

        serverCardW = Math.min(420, contentW - 40);
        serverCardH = 48;
        serverCardGap = 6;
        serverListY = statsY + 30;
        int maxCards = Math.max(0, (contentY + contentH - serverListY - 10) / (serverCardH + serverCardGap));

        if (!servers.isEmpty()) {
            g.text(font, Component.literal("Recent Servers"), contentX + (contentW - serverCardW) / 2, serverListY - 18,
                Theme.FOREGROUND);
        }

        int srvY = serverListY;
        for (int i = 0; i < servers.size() && i < maxCards; i++) {
            ServerData s = servers.get(i);
            int cx0 = contentX + (contentW - serverCardW) / 2;
            boolean hover = mx >= cx0 && mx <= cx0 + serverCardW && my >= srvY && my <= srvY + serverCardH;
            if (hover) hoveredServer = i;

            int fill = hover
                ? ColorUtil.lerpARGB(ColorUtil.withAlpha(Theme.CARD, 230), ColorUtil.withAlpha(accent, 90), 0.5f)
                : ColorUtil.withAlpha(Theme.CARD, 230);
            Panel.drawElevated(g, cx0, srvY, serverCardW, serverCardH, fill, hover ? Theme.ELEVATION_1 : Theme.ELEVATION_0);
            Panel.drawHollowRect(g, cx0, srvY, serverCardW, serverCardH, hover ? ColorUtil.withAlpha(accent, 140) : Theme.BORDER_LIGHT);

            g.text(font, Component.literal(s.name), cx0 + 12, srvY + 6, Theme.FOREGROUND);
            g.text(font, Component.literal(s.ip), cx0 + 12, srvY + 17,
                ColorUtil.withAlpha(Theme.MUTED_FOREGROUND, 160));
            String ping = s.ping < 0 ? "\u00B7\u00B7\u00B7" : s.ping + "ms";
            int pingColor = s.ping <= 80 ? 0xFF66E08A : (s.ping <= 160 ? 0xFFF0C463 : 0xFFF26A6A);
            g.text(font, Component.literal(ping), cx0 + serverCardW - font.width(ping) - 12, srvY + 8, pingColor);
            g.text(font, Component.literal("\u25B6"), cx0 + serverCardW - 28, srvY + 18,
                hover ? accent : ColorUtil.withAlpha(Theme.MUTED_FOREGROUND, 120));

            srvY += serverCardH + serverCardGap;
        }
        if (hoveredServer != -1 && hoveredServer != lastHoveredServer) UiSounds.hover();
        lastHoveredServer = hoveredServer;
        hoveredServer = -1;
    }

    private void drawQuickLink(GuiGraphicsExtractor g, String label, int x, int y, int w, int h, boolean hover, int accent) {
        int fill = hover ? ColorUtil.withAlpha(Theme.MUTED, 130) : ColorUtil.withAlpha(Theme.BACKGROUND, 120);
        Panel.draw(g, x, y, w, h, fill);
        Panel.drawHollowRect(g, x, y, w, h, hover ? accent : Theme.BORDER_LIGHT);
        g.text(font, Component.literal(label), x + (w - font.width(label)) / 2, y + (h - 9) / 2,
            hover ? accent : Theme.FOREGROUND);
    }

    // ------------------------------------------------------------------ mods

    private void renderMods(GuiGraphicsExtractor g, float delta) {
        renderModsTop(g);

        List<ModGroup> groups = visibleGroups();
        if (groups.isEmpty()) {
            String msg = "No modules match";
            g.text(font, Component.literal(msg), contentX + (contentW - font.width(msg)) / 2, modsGridY() + 40,
                Theme.MUTED_FOREGROUND);
            return;
        }

        int contentH = modsContentHeight();
        int gridTop = modsGridY();
        int gridH = modsGridH();
        int frameMax = Math.max(0, contentH - gridH);
        if (frameMax != maxScroll) {
            maxScroll = frameMax;
            scrollTarget = Anim.clamp(scrollTarget, 0, maxScroll);
            scrollOffset = Anim.clamp(scrollOffset, 0, maxScroll);
        }
        scrollOffset += (scrollTarget - scrollOffset) * 0.35f;
        if (Math.abs(scrollOffset - scrollTarget) < 0.01f) scrollOffset = scrollTarget;

        g.enableScissor(contentX, gridTop, contentX + contentW, gridTop + gridH);

        int accent = Theme.getAnimatedAccent();
        hoveredSection = -1;
        hoveredModInSection = -1;
        cascadeCardIndex = 0;
        cascadeCardTotal = 0;
        for (ModGroup grp : groups) cascadeCardTotal += grp.mods.size();
        int y = gridTop - (int) scrollOffset;
        int sectionIdx = 0;
        for (ModGroup group : groups) {
            int headerH = group.title == null ? 0 : SECTION_HEADER_H;
            int rows = gridRows(group.mods.size());
            int bodyH = rows * MODULE_CARD_H + Math.max(0, rows - 1) * MODULE_CARD_GAP;
            int totalH = headerH + (bodyH > 0 ? bodyH + 4 : 0);
            if (totalH <= 0) {
                y += totalH + SECTION_GAP;
                sectionIdx++;
                continue;
            }

            if (y + totalH > gridTop && y < gridTop + gridH) {
                renderModGroup(g, group, y, accent, delta);
            }
            if (mx >= contentX && mx <= contentX + contentW && my >= y && my <= y + totalH) {
                hoveredSection = sectionIdx;
                if (bodyH > 0 && my >= y + headerH + 4) {
                    int cols = Math.min(GRID_COLS, group.mods.size());
                    int colW = (contentW - 12 - (cols - 1) * MODULE_CARD_GAP) / cols;
                    int gy = y + headerH + 4;
                    for (int mi = 0; mi < group.mods.size(); mi++) {
                        int ci = mi % cols;
                        int ri = mi / cols;
                        int mcx = contentX + 6 + ci * (colW + MODULE_CARD_GAP);
                        int mcy = gy + ri * (MODULE_CARD_H + MODULE_CARD_GAP);
                        if (mx >= mcx && mx <= mcx + colW && my >= mcy && my <= mcy + MODULE_CARD_H) {
                            hoveredModInSection = mi;
                            break;
                        }
                    }
                }
            }
            y += totalH + SECTION_GAP;
            sectionIdx++;
        }

        if (hoveredSection != -1 && hoveredSection != lastHoveredSection) UiSounds.hover();
        lastHoveredSection = hoveredSection;
        if (hoveredModInSection != -1 && hoveredModInSection != lastHoveredModIdx) UiSounds.hover();
        lastHoveredModIdx = hoveredModInSection;

        g.disableScissor();

        if (maxScroll > 0) {
            float thumbH = (float) gridH / contentH * gridH;
            float thumbY = (scrollOffset / Math.max(1, maxScroll)) * (gridH - thumbH);
            int tx = contentX + contentW - 6;
            int ty = gridTop + (int) thumbY;
            int th = Math.max(8, (int) thumbH);
            boolean overTrack = mx >= tx - 3 && mx <= tx + 7 && my >= gridTop && my <= gridTop + gridH;
            int trackAlpha = (overTrack || draggingScrollbar) ? 120 : 60;
            g.fill(tx - 2, gridTop, tx + 6, gridTop + gridH, ColorUtil.withAlpha(Theme.GLASS_BG, trackAlpha));
            int thumbColor = overThumb(mx, my) || draggingScrollbar ? accent : ColorUtil.withAlpha(Theme.BORDER_LIGHT, 200);
            g.fill(tx, ty, tx + 4, ty + th, thumbColor);
        }
    }

    private void renderModsTop(GuiGraphicsExtractor g) {
        int sbW = contentW - 44 - 40;
        searchBar.render(g, font, contentX, contentY, sbW, mx, my, 0);

        filterBtnX = contentX + sbW + 4;
        filterBtnY = contentY;
        boolean filterHover = mx >= filterBtnX && mx <= filterBtnX + 36 && my >= filterBtnY && my <= filterBtnY + 36;
        int fbFill = filterHover ? ColorUtil.withAlpha(Theme.MUTED, 120) : ColorUtil.withAlpha(Theme.BACKGROUND, 160);
        g.fill(filterBtnX, filterBtnY, filterBtnX + 36, filterBtnY + 36, fbFill);
        Panel.drawHollowRect(g, filterBtnX, filterBtnY, 36, 36, filterHover ? Theme.getAnimatedAccent() : Theme.BORDER_LIGHT);
        g.text(font, Component.literal("\u22EE"), filterBtnX + 10, filterBtnY + 10, filterHover ? Theme.FOREGROUND : Theme.MUTED_FOREGROUND);
        filterMenu.render(g, font, mx, my);

        gearBtnX = filterBtnX + 40;
        boolean gearHover = mx >= gearBtnX && mx <= gearBtnX + 36 && my >= filterBtnY && my <= filterBtnY + 36;
        if (gearHover != lastGearHover) { if (gearHover) UiSounds.hover(); lastGearHover = gearHover; }
        int gearFill = gearHover ? ColorUtil.withAlpha(Theme.MUTED, 120) : ColorUtil.withAlpha(Theme.BACKGROUND, 160);
        g.fill(gearBtnX, filterBtnY, gearBtnX + 36, filterBtnY + 36, gearFill);
        Panel.drawHollowRect(g, gearBtnX, filterBtnY, 36, 36, gearHover ? Theme.getAnimatedAccent() : Theme.BORDER_LIGHT);
        g.text(font, Component.literal("\u2699"), gearBtnX + 8, filterBtnY + 10, gearHover ? Theme.FOREGROUND : Theme.MUTED_FOREGROUND);
        if (filterHover != lastFilterHover) { if (filterHover) UiSounds.hover(); lastFilterHover = filterHover; }

        int chipsY = contentY + SEARCH_H + 8;
        List<String> chips = chips();
        int totalW = 0;
        for (String c : chips) totalW += chipWidth(c);
        totalW += (chips.size() - 1) * 6;
        chipMaxScroll = Math.max(0, totalW - contentW);
        chipScroll = Anim.clamp(chipScroll, 0, chipMaxScroll);

        int cx = contentX - (int) chipScroll;
        int hovered = -1;
        for (int i = 0; i < chips.size(); i++) {
            String c = chips.get(i);
            int cw = chipWidth(c);
            boolean selected = (c == null) ? (category == null) : (c.equals(FAV_CAT) ? FAV_CAT.equals(category) : c.equals(category));
            boolean hover = mx >= cx && mx <= cx + cw && my >= chipsY && my <= chipsY + CHIP_H;
            if (hover) hovered = i;
            int fill = selected
                ? ColorUtil.withAlpha(Theme.getAnimatedAccent(), 210)
                : (hover ? ColorUtil.withAlpha(Theme.MUTED, 120) : ColorUtil.withAlpha(Theme.BACKGROUND, 140));
            Panel.draw(g, cx, chipsY, cw, CHIP_H, fill);
            Panel.drawHollowRect(g, cx, chipsY, cw, CHIP_H, selected ? Theme.getAnimatedAccent() : Theme.BORDER_LIGHT);
            String label = c == null ? "All" : (c.equals(FAV_CAT) ? "Favorites" : c);
            g.text(font, Component.literal(label), cx + (cw - font.width(label)) / 2, chipsY + (CHIP_H - 9) / 2,
                selected ? 0xFFFFFFFF : (hover ? Theme.FOREGROUND : Theme.MUTED_FOREGROUND));
            cx += cw + 6;
        }
        if (hovered != -1 && hovered != lastHoveredChip) UiSounds.hover();
        lastHoveredChip = hovered;
    }

    private int chipWidth(String c) {
        String label = c == null ? "All" : (c.equals(FAV_CAT) ? "Favorites" : c);
        return font.width(label) + 24;
    }

    private List<String> chips() {
        List<String> list = new ArrayList<>();
        list.add(null); // All
        list.add(FAV_CAT);
        for (String cat : CrestModules.getCategories()) {
            if (!cat.equals(FAV_CAT)) list.add(cat);
        }
        return list;
    }

    private void renderModGroup(GuiGraphicsExtractor g, ModGroup group, int y, int accent, float delta) {
        if (group.title != null) {
            g.text(font, Component.literal(group.title), contentX + 4, y + 6, Theme.FOREGROUND);
            String info = group.mods.size() + " module" + (group.mods.size() != 1 ? "s" : "");
            g.text(font, Component.literal(info), contentX + contentW - font.width(info) - 4, y + 6,
                ColorUtil.withAlpha(Theme.MUTED_FOREGROUND, 140));
        }

        int bodyH = group.mods.size() * (MODULE_CARD_H + MODULE_CARD_GAP) - MODULE_CARD_GAP;
        if (bodyH <= 0) return;
        int gy = y + (group.title == null ? 0 : SECTION_HEADER_H + 4);

        int cols = Math.min(GRID_COLS, group.mods.size());
        int colW = (contentW - 12 - (cols - 1) * MODULE_CARD_GAP) / cols;
        for (int mi = 0; mi < group.mods.size(); mi++) {
            int ci = mi % cols;
            int ri = mi / cols;
            int cx = contentX + 6 + ci * (colW + MODULE_CARD_GAP);
            int cy = gy + ri * (MODULE_CARD_H + MODULE_CARD_GAP);
            renderModuleCard(g, group.mods.get(mi), cx, cy, colW, accent, delta);
        }
    }

    private void renderModuleCard(GuiGraphicsExtractor g, CrestModule mod, int cx, int cy, int cw, int accent, float delta) {
        String id = mod.getId();
        boolean enabled = CrestModules.isEnabled(id);

        float open = anim.value();
        float staggerPerCard = cascadeCardTotal > 0 ? Math.min(0.04f, 0.55f / cascadeCardTotal) : 0;
        float cardStart = cascadeCardIndex * staggerPerCard;
        float cardProgress = Anim.clamp((open - cardStart) / 0.18f, 0, 1);
        cascadeCardIndex++;
        if (!anim.isEnabled() || cardProgress < 0.01f) return;

        float scale = 0.95f + 0.05f * Anim.easeOutBack(cardProgress);
        float ccx = cx + cw / 2f;
        float ccy = cy + MODULE_CARD_H / 2f;
        g.pose().pushMatrix();
        g.pose().translate(ccx, ccy);
        g.pose().scale(scale, scale);
        g.pose().translate(-ccx, -ccy);

        boolean hover = mx >= cx && mx <= cx + cw && my >= cy && my <= cy + MODULE_CARD_H;

        Animated ha = rowHoverAnims.computeIfAbsent(id, k -> new Animated(0f, 12f));
        ha.set(hover ? 1f : 0f);
        ha.tick(delta);
        float hoverAmt = ha.get();

        int tint = enabled ? ColorUtil.withAlpha(Theme.CARD, 230) : ColorUtil.withAlpha(Theme.BACKGROUND, 140);
        if (hoverAmt > 0.01f) {
            tint = ColorUtil.lerpARGB(tint, ColorUtil.withAlpha(Theme.MUTED, 150), hoverAmt * 0.35f);
        }
        Panel.drawElevated(g, cx, cy, cw, MODULE_CARD_H, tint, enabled ? Theme.ELEVATION_1 : Theme.ELEVATION_0);

        int borderCol = enabled ? ColorUtil.withAlpha(accent, 70) : ColorUtil.withAlpha(Theme.BORDER_LIGHT, 50);
        Panel.drawHollowRect(g, cx, cy, cw, MODULE_CARD_H, borderCol);

        int stripe = enabled ? accent : (hover ? ColorUtil.withAlpha(accent, 100) : ColorUtil.withAlpha(accent, 30));
        g.fill(cx, cy, cx + 2, cy + MODULE_CARD_H, stripe);

        int lx = cx + 10;
        g.text(font, Component.literal(mod.getName()), lx, cy + 10,
            enabled ? Theme.FOREGROUND : Theme.MUTED_FOREGROUND);

        String desc = mod.getDescription();
        if (desc != null && !desc.isEmpty()) {
            int descMaxW = cw - 16;
            String truncated = font.width(desc) > descMaxW
                ? font.plainSubstrByWidth(desc, descMaxW - 4) + "\u2026"
                : desc;
            g.text(font, Component.literal(truncated), lx, cy + 26, ColorUtil.withAlpha(Theme.MUTED_FOREGROUND, 150));
        }

        String cat = mod.getCategory();
        g.text(font, Component.literal(cat), lx, cy + MODULE_CARD_H - 12, ColorUtil.withAlpha(Theme.MUTED_FOREGROUND, 90));

        int starX = cx + cw - TOGGLE_W - 4;
        boolean starHover = hover && mx >= starX - 6 && mx <= starX + 18 && my >= cy + 6 && my <= cy + 30;
        int starCol = isFavorite(id)
            ? ColorUtil.lerpARGB(Theme.MUTED_FOREGROUND, accent, 1f)
            : (starHover ? accent : Theme.MUTED_FOREGROUND);
        g.text(font, Component.literal(isFavorite(id) ? "\u2605" : "\u2606"), starX, cy + 10, starCol);

        Animated ta = toggleAnims.computeIfAbsent(id, k -> new Animated(0f, 12f));
        ta.set(enabled ? 1f : 0f);
        ta.tick(delta);
        int toggleX = cx + cw - TOGGLE_W - 4;
        int toggleY = cy + MODULE_CARD_H - TOGGLE_H - 6;
        drawToggle(g, toggleX, toggleY, enabled, ta.get());

        g.pose().popMatrix();
    }

    private void drawToggle(GuiGraphicsExtractor g, int x, int y, boolean on, float anim) {
        int trackOff = 0x1AFFFFFF;
        int trackOn = ColorUtil.lerpARGB(0x1AFFFFFF, Theme.getAnimatedAccent(), 0.9f);
        int trackColor = ColorUtil.lerpARGB(trackOff, trackOn, anim);
        g.fill(x, y, x + TOGGLE_W, y + TOGGLE_H, trackColor);
        Panel.drawHollowRect(g, x, y, TOGGLE_W, TOGGLE_H, Theme.BORDER_LIGHT);

        int knobMinX = x + 3;
        int knobMaxX = x + TOGGLE_W - 19;
        int knobX = (int) Anim.lerp(knobMinX, knobMaxX, anim);
        int knobColor = ColorUtil.lerpARGB(Theme.MUTED_FOREGROUND, Theme.PRIMARY, anim);
        g.fill(knobX, y + 3, knobX + 16, y + TOGGLE_H - 3, knobColor);
    }

    private List<ModGroup> visibleGroups() {
        List<ModGroup> groups = new ArrayList<>();
        String q = searchBar.getText();
        boolean searching = !q.isEmpty();
        if (category == null && !searching) {
            for (String cat : CrestModules.getCategories()) {
                List<CrestModule> mods = visibleModules(cat);
                if (!mods.isEmpty()) groups.add(new ModGroup(cat, mods));
            }
        } else {
            List<CrestModule> mods;
            if (FAV_CAT.equals(category)) {
                mods = CrestModules.getAll().values().stream()
                    .filter(m -> isFavorite(m.getId()))
                    .collect(Collectors.toList());
            } else if (category != null) {
                mods = CrestModules.getByCategory(category);
            } else {
                mods = new ArrayList<>(CrestModules.getAll().values());
            }
            mods = applyFilterAndSearch(mods);
            if (!mods.isEmpty()) groups.add(new ModGroup(null, mods));
        }
        return groups;
    }

    private List<CrestModule> visibleModules(String cat) {
        List<CrestModule> mods;
        if (FAV_CAT.equals(cat)) {
            mods = CrestModules.getAll().values().stream()
                .filter(m -> isFavorite(m.getId()))
                .collect(Collectors.toList());
        } else {
            mods = CrestModules.getByCategory(cat);
        }
        return applyFilterAndSearch(mods);
    }

    private List<CrestModule> applyFilterAndSearch(List<CrestModule> mods) {
        String q = searchBar.getText();
        List<CrestModule> out = new ArrayList<>();
        for (CrestModule m : mods) {
            if (filterMode == 1 && !CrestModules.isEnabled(m.getId())) continue;
            if (filterMode == 2 && CrestModules.isEnabled(m.getId())) continue;
            if (!q.isEmpty() && !SearchBar.fuzzyMatch(q, m.getName()) && !SearchBar.fuzzyMatch(q, m.getId())) continue;
            out.add(m);
        }
        if (!sortAsc) Collections.reverse(out);
        return out;
    }

    private int modsContentHeight() {
        int h = 0;
        for (ModGroup group : visibleGroups()) {
            int headerH = group.title == null ? 0 : SECTION_HEADER_H;
            int rows = gridRows(group.mods.size());
            int bodyH = rows * MODULE_CARD_H + Math.max(0, rows - 1) * MODULE_CARD_GAP;
            int totalH = headerH + (bodyH > 0 ? bodyH + 4 : 0);
            if (totalH <= 0) continue;
            h += totalH + SECTION_GAP;
        }
        return Math.max(0, h - SECTION_GAP);
    }

    private int gridRows(int count) {
        return count == 0 ? 0 : (count - 1) / GRID_COLS + 1;
    }

    private boolean overThumb(int mx, int my) {
        if (maxScroll <= 0) return false;
        int tx = contentX + contentW - 6;
        int contentH = modsContentHeight();
        float thumbH = (float) modsGridH() / contentH * modsGridH();
        float thumbY = (scrollOffset / Math.max(1, maxScroll)) * (modsGridH() - thumbH);
        return mx >= tx - 3 && mx <= tx + 7 && my >= modsGridY() + thumbY && my <= modsGridY() + thumbY + thumbH;
    }

    private int[] hitMod(int gx, int gy) {
        List<ModGroup> groups = visibleGroups();
        int y = modsGridY() - (int) scrollOffset;
        for (int gi = 0; gi < groups.size(); gi++) {
            ModGroup grp = groups.get(gi);
            int headerH = grp.title == null ? 0 : SECTION_HEADER_H;
            int rows = gridRows(grp.mods.size());
            int bodyH = rows * MODULE_CARD_H + Math.max(0, rows - 1) * MODULE_CARD_GAP;
            int totalH = headerH + (bodyH > 0 ? bodyH + 4 : 0);
            if (totalH <= 0) {
                y += totalH + SECTION_GAP;
                continue;
            }
            if (gy >= y && gy <= y + totalH) {
                if (bodyH > 0 && gy >= y + headerH + 4) {
                    int gy0 = y + headerH + 4;
                    int cols = Math.min(GRID_COLS, grp.mods.size());
                    int colW = (contentW - 12 - (cols - 1) * MODULE_CARD_GAP) / cols;
                    for (int mi = 0; mi < grp.mods.size(); mi++) {
                        int ci = mi % cols;
                        int ri = mi / cols;
                        int mcx = contentX + 6 + ci * (colW + MODULE_CARD_GAP);
                        int mcy = gy0 + ri * (MODULE_CARD_H + MODULE_CARD_GAP);
                        if (gx >= mcx && gx <= mcx + colW && gy >= mcy && gy <= mcy + MODULE_CARD_H) {
                            return new int[]{gi, mi};
                        }
                    }
                }
                return new int[]{gi, -1};
            }
            y += totalH + SECTION_GAP;
        }
        return null;
    }

    // ------------------------------------------------------------------ settings

    private void renderSettings(GuiGraphicsExtractor g, float delta) {
        int accent = Theme.getAnimatedAccent();

        int ry = contentY;
        int hovered = -1;
        for (int i = 0; i < settingsEntries.length; i++) {
            int eY = ry + i * 42;
            boolean hover = mx >= contentX && mx <= contentX + RAIL_W && my >= eY && my <= eY + 38;
            if (hover) hovered = i;
            if (hover) {
                g.fill(contentX, eY, contentX + RAIL_W, eY + 38, ColorUtil.withAlpha(Theme.MUTED, 100));
            }
            g.fill(contentX, eY, contentX + 3, eY + 38, ColorUtil.withAlpha(accent, hover ? 200 : 40));
            g.text(font, Component.literal(settingsEntries[i]), contentX + 18, eY + 14,
                hover ? accent : Theme.FOREGROUND);
        }
        if (hovered != -1 && hovered != lastHoveredEntry) UiSounds.hover();
        lastHoveredEntry = hovered;

        int px = contentX + RAIL_W + 20;
        int pw = contentW - RAIL_W - 20;
        int pad = 18;
        int innerW = pw - pad * 2;
        Panel.drawElevated(g, px, contentY, pw, contentH, ColorUtil.withAlpha(Theme.CARD, 235), Theme.ELEVATION_1);
        Panel.drawHollowRect(g, px, contentY, pw, contentH, Theme.BORDER_LIGHT);

        int cy = contentY + pad;

        g.fill(px + pad, cy, px + pad + 3, cy + font.lineHeight + 2, accent);
        g.text(font, Component.literal("Quick Settings"), px + pad + 8, cy, Theme.FOREGROUND);
        cy += font.lineHeight + 16;

        blurW = innerW;
        blurX = px + pad;
        blurY = cy;
        renderInlineToggle(g, "Menu Blur", blurX, blurY, blurW, Theme.menuBlur, accent);
        cy += 32;

        accentX = px + pad;
        accentY = cy;
        renderInlineToggle(g, "Animated Accent", accentX, accentY, blurW, Theme.accentAnim, accent);
        cy += 40;

        g.fill(px + pad, cy, px + pw - pad, cy + 1, ColorUtil.withAlpha(Theme.BORDER_LIGHT, 60));
        cy += 12;

        g.fill(px + pad, cy, px + pad + 3, cy + font.lineHeight + 2, accent);
        g.text(font, Component.literal("Theme"), px + pad + 8, cy, Theme.FOREGROUND);
        cy += font.lineHeight + 14;

        String[] presets = {"Lunar", "Dark", "Light", "Amoled"};
        themeBtnW = (innerW - 3 * 8) / 4;
        themeBtnH = 32;
        themeBtnX = px + pad;
        themeBtnY = cy;
        int hoveredBtn = -1;
        for (int i = 0; i < 4; i++) {
            int bx = px + pad + i * (themeBtnW + 8);
            int by = cy;
            boolean hover = mx >= bx && mx <= bx + themeBtnW && my >= by && my <= by + themeBtnH;
            if (hover) hoveredBtn = i;
            boolean active = Theme.get().preset != null && Theme.get().preset.equalsIgnoreCase(presets[i]);
            int fill = active
                ? ColorUtil.withAlpha(accent, 210)
                : (hover ? ColorUtil.withAlpha(Theme.MUTED, 130) : ColorUtil.withAlpha(Theme.BACKGROUND, 140));
            Panel.draw(g, bx, by, themeBtnW, themeBtnH, fill);
            Panel.drawHollowRect(g, bx, by, themeBtnW, themeBtnH, active ? accent : Theme.BORDER_LIGHT);
            g.text(font, Component.literal(presets[i]), bx + (themeBtnW - font.width(presets[i])) / 2, by + (themeBtnH - 9) / 2,
                active ? 0xFFFFFFFF : (hover ? accent : Theme.FOREGROUND));
        }
        if (hoveredBtn != -1 && hoveredBtn != lastHoveredThemeBtn) UiSounds.hover();
        lastHoveredThemeBtn = hoveredBtn;
        cy += themeBtnH + 16;

        g.fill(px + pad, cy, px + pw - pad, cy + 1, ColorUtil.withAlpha(Theme.BORDER_LIGHT, 60));
        cy += 12;

        int enabled = (int) CrestModules.getAll().values().stream()
            .filter(m -> CrestModules.isEnabled(m.getId())).count();
        String info = CrestModules.getAll().size() + " modules  \u2022  " + enabled + " enabled";
        g.text(font, Component.literal(info), px + pad, cy, ColorUtil.withAlpha(Theme.MUTED_FOREGROUND, 160));
    }

    private void renderInlineToggle(GuiGraphicsExtractor g, String label, int x, int y, int w, boolean on, int accent) {
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

    // ------------------------------------------------------------------ input

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (inputBlocked()) return true;
        if (tab == Tab.MODS && searchBar.charTyped(event.codepoint(), 0)) {
            scrollTarget = 0;
            scrollOffset = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (inputBlocked()) return true;

        if (tab == Tab.MODS) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !searchBar.getText().isEmpty()) {
                searchBar.keyPressed(key, 0, 0);
                scrollTarget = 0;
                scrollOffset = 0;
                return true;
            }
            if (filterMenu.open && filterMenu.keyPressed(key, 0, event.modifiers())) return true;
            if (key == GLFW.GLFW_KEY_TAB) {
                cycleCategory(event.modifiers());
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                toggleHoveredModule();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void cycleCategory(int mods) {
        List<String> chips = chips();
        if (chips.isEmpty()) return;
        int idx = chips.indexOf(category);
        int next = mods == 1 ? (idx - 1 + chips.size()) % chips.size() : (idx + 1) % chips.size();
        category = chips.get(next);
        searchBar.setText("");
        scrollTarget = scrollOffset = 0;
    }

    private void toggleHoveredModule() {
        if (hoveredSection < 0 || hoveredModInSection < 0) return;
        List<ModGroup> groups = visibleGroups();
        if (hoveredSection >= groups.size()) return;
        ModGroup grp = groups.get(hoveredSection);
        if (hoveredModInSection >= grp.mods.size()) return;
        CrestModule mod = grp.mods.get(hoveredModInSection);
        CrestModules.setEnabled(mod.getId(), !CrestModules.isEnabled(mod.getId()));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (inputBlocked()) return true;
        double mxx = event.x(), myy = event.y();
        int btn = event.buttonInfo().input();
        if (btn != 0) return super.mouseClicked(event, doubleClick);

        if (filterMenu.mouseClicked(mxx, myy, btn)) return true;

        int gap = 4;
        int tabW = 72;
        int tabsW = Tab.values().length * tabW + (Tab.values().length - 1) * gap;
        int startX = pX + pW - 16 - tabsW;
        int tabY = pY + (TOPBAR_H - 26) / 2;
        for (int i = 0; i < Tab.values().length; i++) {
            int x = startX + i * (tabW + gap);
            if (mxx >= x && mxx <= x + tabW && myy >= tabY && myy <= tabY + 26) {
                UiSounds.click();
                tab = Tab.values()[i];
                return true;
            }
        }

        switch (tab) {
            case HOME -> { if (handleHomeClick(mxx, myy)) return true; }
            case MODS -> { if (handleModsClick(mxx, myy)) return true; }
            case SETTINGS -> { if (handleSettingsClick(mxx, myy)) return true; }
        }

        if (quickSettings.mouseClicked(mxx, myy, btn)) {
            UiSounds.click();
            return true;
        }

        if (miniPlayer.mouseClicked(mxx, myy)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    private boolean handleHomeClick(double mxx, double myy) {
        if (mxx >= playX && mxx <= playX + playW && myy >= playY && myy <= playY + playH) {
            UiSounds.click();
            play();
            return true;
        }
        int linkW = 120, linkH = 34, linkY = playY + playH + 34;
        int linkX0 = contentX + contentW / 2 - linkW - 6;
        int linkX1 = contentX + contentW / 2 + 6;
        if (mxx >= linkX0 && mxx <= linkX0 + linkW && myy >= linkY && myy <= linkY + linkH) {
            UiSounds.click();
            tab = Tab.MODS;
            return true;
        }
        if (mxx >= linkX1 && mxx <= linkX1 + linkW && myy >= linkY && myy <= linkY + linkH) {
            UiSounds.click();
            tab = Tab.SETTINGS;
            return true;
        }

        int cx0 = contentX + (contentW - serverCardW) / 2;
        for (int i = 0; i < servers.size(); i++) {
            int srvY = serverListY + i * (serverCardH + serverCardGap);
            if (mxx >= cx0 && mxx <= cx0 + serverCardW && myy >= srvY && myy <= srvY + serverCardH) {
                UiSounds.click();
                connect(servers.get(i));
                return true;
            }
        }
        return false;
    }

    private boolean handleModsClick(double mxx, double myy) {
        int tx = contentX + contentW - 6;
        if (maxScroll > 0 && mxx >= tx - 3 && mxx <= tx + 7 && myy >= modsGridY() && myy <= modsGridY() + modsGridH()) {
            int contentH = modsContentHeight();
            float thumbH = (float) modsGridH() / contentH * modsGridH();
            float thumbY = (scrollOffset / Math.max(1, maxScroll)) * (modsGridH() - thumbH);
            if (myy >= modsGridY() + thumbY && myy <= modsGridY() + thumbY + thumbH) {
                draggingScrollbar = true;
                scrollbarDragStartY = (int) myy;
                scrollbarDragStartOffset = scrollOffset;
            } else if (maxScroll > 0) {
                scrollTarget = Anim.clamp((float) (myy - modsGridY()) / modsGridH() * maxScroll, 0, maxScroll);
            }
            return true;
        }

        if (mxx >= filterBtnX && mxx <= filterBtnX + 36 && myy >= filterBtnY && myy <= filterBtnY + 36) {
            UiSounds.click();
            filterMenu.toggle(filterBtnX, filterBtnY + 36, 36, 36);
            return true;
        }
        if (mxx >= gearBtnX && mxx <= gearBtnX + 36 && myy >= filterBtnY && myy <= filterBtnY + 36) {
            UiSounds.click();
            quickSettings.toggle();
            return true;
        }

        if (searchBar.mouseClicked(mxx, myy, 0)) {
            scrollTarget = scrollOffset = 0;
            return true;
        }

        int chipsY = contentY + SEARCH_H + 8;
        List<String> chips = chips();
        int cx = contentX - (int) chipScroll;
        for (int i = 0; i < chips.size(); i++) {
            String c = chips.get(i);
            int cw = chipWidth(c);
            if (mxx >= cx && mxx <= cx + cw && myy >= chipsY && myy <= chipsY + CHIP_H) {
                UiSounds.click();
                category = (c == null) ? null : (category != null && category.equals(c) ? null : c);
                scrollTarget = scrollOffset = 0;
                return true;
            }
            cx += cw + 6;
        }

        int[] hit = hitMod((int) mxx, (int) myy);
        if (hit != null) {
            List<ModGroup> groups = visibleGroups();
            ModGroup grp = groups.get(hit[0]);
            if (hit[1] >= 0) {
                CrestModule mod = grp.mods.get(hit[1]);
                int headerH = grp.title == null ? 0 : SECTION_HEADER_H;
                int rows = gridRows(grp.mods.size());
                int gy0 = modsGridY() - (int) scrollOffset + headerH + 4;
                int cols = Math.min(GRID_COLS, grp.mods.size());
                int colW = (contentW - 12 - (cols - 1) * MODULE_CARD_GAP) / cols;
                int ci = hit[1] % cols;
                int ri = hit[1] / cols;
                int mcx = contentX + 6 + ci * (colW + MODULE_CARD_GAP);
                int mcy = gy0 + ri * (MODULE_CARD_H + MODULE_CARD_GAP);
                int starX = mcx + colW - TOGGLE_W - 4;
                int toggleX = mcx + colW - TOGGLE_W - 4;
                int toggleY = mcy + MODULE_CARD_H - TOGGLE_H - 6;
                if (mxx >= starX - 6 && mxx <= starX + 18 && myy >= mcy + 6 && myy <= mcy + 30) {
                    UiSounds.click();
                    toggleFavorite(mod.getId());
                    return true;
                }
                if (mxx >= toggleX && mxx <= toggleX + TOGGLE_W && myy >= toggleY && myy <= toggleY + TOGGLE_H) {
                    UiSounds.click();
                    CrestModules.setEnabled(mod.getId(), !CrestModules.isEnabled(mod.getId()));
                    return true;
                }
                UiSounds.click();
                Screen config = mod.createConfigScreen(this);
                navigate(config != null ? config : new ModuleDetailScreen(mod, this));
                return true;
            }
            return true;
        }
        return false;
    }

    private boolean handleSettingsClick(double mxx, double myy) {
        for (int i = 0; i < settingsEntries.length; i++) {
            int eY = contentY + i * 42;
            if (mxx >= contentX && mxx <= contentX + RAIL_W && myy >= eY && myy <= eY + 38) {
                UiSounds.click();
                openSetting(i);
                return true;
            }
        }

        if (mxx >= blurX && mxx <= blurX + blurW && myy >= blurY && myy <= blurY + 24) {
            UiSounds.click();
            ThemeData d = Theme.get();
            d.menuBlur = !d.menuBlur;
            Theme.apply(d);
            return true;
        }
        if (mxx >= accentX && mxx <= accentX + blurW && myy >= accentY && myy <= accentY + 24) {
            UiSounds.click();
            ThemeData d = Theme.get();
            d.accentAnim = !d.accentAnim;
            Theme.apply(d);
            return true;
        }

        String[] presets = {"Lunar", "Dark", "Light", "Amoled"};
        for (int i = 0; i < 4; i++) {
            int bx = themeBtnX + i * (themeBtnW + 8);
            int by = themeBtnY;
            if (mxx >= bx && mxx <= bx + themeBtnW && myy >= by && myy <= by + themeBtnH) {
                UiSounds.click();
                Theme.apply(ThemePresets.fromName(presets[i]));
                Theme.save();
                return true;
            }
        }
        return false;
    }

    private void openSetting(int i) {
        switch (i) {
            case 0 -> navigate(new HudEditScreen());
            case 1 -> navigate(new ThemeEditorScreen(this));
            case 2 -> navigate(new AnimationsScreen(this));
            case 3 -> navigate(new ProfileScreen(this));
            case 4 -> navigate(new MusicScreen(MusicModule.getPlayer(), this));
            case 5 -> navigate(new ResourcePackBrowserScreen(this));
            case 6 -> navigate(new StreamerSettingsScreen(this));
        }
    }

    private void play() {
        if (!servers.isEmpty()) {
            connect(servers.get(0));
        } else {
            minecraft.setScreen(new JoinMultiplayerScreen(this));
        }
    }

    private void connect(ServerData data) {
        ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(data.ip), data, false, null);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingScrollbar && maxScroll > 0) {
            float dScroll = (float) (event.y() - scrollbarDragStartY) / modsGridH() * maxScroll;
            scrollTarget = Anim.clamp(scrollbarDragStartOffset + dScroll, 0, maxScroll);
            scrollOffset = scrollTarget;
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (inputBlocked()) return true;
        int chipsY = contentY + SEARCH_H + 8;
        if (tab == Tab.MODS && mouseY >= chipsY && mouseY <= chipsY + CHIP_H) {
            chipScroll = Anim.clamp(chipScroll - (float) deltaY * 40, 0, chipMaxScroll);
            return true;
        }
        if (tab == Tab.MODS && mouseY >= modsGridY()) {
            int contentH = modsContentHeight();
            maxScroll = Math.max(0, contentH - modsGridH());
            if (maxScroll == 0) return true;
            scrollTarget = Anim.clamp(scrollTarget - (float) deltaY * 25, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private static boolean isFavorite(String id) {
        return CrestModules.getConfigManager().getBoolean("crest_client", "fav:" + id);
    }

    private static void toggleFavorite(String id) {
        boolean next = !isFavorite(id);
        CrestModules.getConfigManager().set("crest_client", "fav:" + id, next);
        CrestModules.getConfigManager().save();
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new CrestMenu());
    }

    /** Cross-fade into a sub-screen: exit anim, then swap on completion. */
    private void navigate(Screen target) {
        anim.closeTo(target);
    }

    @Override
    public void onClose() {
        anim.closeTo(null);
    }
    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean isCapturingText() {
        return tab == Tab.MODS && searchBar.isFocused();
    }

    private record ModGroup(String title, List<CrestModule> mods) {}
}
