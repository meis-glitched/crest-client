package com.crest.client.music;

import com.crest.client.ui.*;
import com.crest.client.core.ScreenAnim;
import com.crest.client.core.TextCapturingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.glfwGetClipboardString;

public class MusicScreen extends Screen implements TextCapturingScreen {
    private static final int BTN_H = 22;
    private static final int BTN_GAP = Spacing.S1;
    private static final int BAR_H = 6;
    private static final int VIZ_BARS = 7;

    private final MusicPlayer player;
    private final Screen backScreen;
    private StringBuilder urlText = new StringBuilder();
    private int urlCursor;
    private boolean urlFocused = true;

    private List<AudioTrack> searchResults;
    private boolean searchPending;
    private int resultScroll;
    private int selectedResult = -1;

    private String statusText = "";
    private int statusTimer;
    private boolean statusIsError;

    private boolean draggingVolume;
    private boolean draggingProgress;
    private boolean draggingDuck;

    private int queueScroll;
    private int queueRowH = 22;
    private int queueRows;

    private final ScreenAnim anim = new ScreenAnim();

    // Eased visual values
    private final Animated progressAnim = new Animated(0f, 12f);
    private final Animated volumeAnim = new Animated(1f, 14f);

    // Hover animation state
    private final Map<String, Animated> hover = new HashMap<>();
    private final Map<String, Boolean> wasHover = new HashMap<>();
    private final Map<Integer, Animated> resHover = new HashMap<>();
    private final Map<Integer, Animated> qHover = new HashMap<>();

    // Geometry reused by input handlers (mirrors last rendered frame)
    private int volBarX, volBarW, volBarY;
    private int nowPlayingBarX, nowPlayingBarW, nowPlayingBarY;
    private int shuffleBtnX, shuffleBtnY, shuffleBtnW, shuffleBtnH;
    private int toggChipY, toggChipH;
    private final int[] toggChipX = new int[4];
    private final int[] toggChipW = new int[4];
    private int duckBarX, duckBarW, duckBarY;
    private int libSetY, libSetH, libLoadY, libLoadH;

    public MusicScreen(MusicPlayer player) {
        this(player, null);
    }

    public MusicScreen(MusicPlayer player, Screen backScreen) {
        super(Component.literal("Crest Music"));
        this.player = player;
        this.backScreen = backScreen;
        anim.begin();
        volumeAnim.setImmediate(player.getSliderVolume() / 100f);
    }

    private int margin() { return Math.max(Spacing.S3, width / 20); }
    private int availW() { return width - margin() * 2; }

    // ---- Layout -------------------------------------------------------------

    private static final class L {
        int m, aw;
        int panelX, panelY, panelW, panelH;
        int closeX, closeY, closeW, closeH;
        int searchX, searchY, searchW, searchH, fieldX, fieldW, goX, goW, folderX, folderW;
        int libX, libW, libY, libH, libRowA, libRowB;
        int setX, setY, setW, setH, loadX, loadY, loadW, loadH;
        int leftX, leftW, rightX, rightW, colTop, colTop2, colBottom;
        int resX, resY, resW, resH, resRowH, resHeaderH;
        int npX, npY, npW, npH, npTitleY, npBarX, npBarY, npBarW, npBarH;
        int npControlsY, npBtnW, npBtnH, npBtnGap;
        int[] npBtnX = new int[5];
        int shuffleX, shuffleY, shuffleW, shuffleH;
        int volY, vizY, vizH, qX, qY, qW, qTop, qBottom;

        static L build(int width, int height) {
            L l = new L();
            l.m = Math.max(Spacing.S3, width / 20);
            l.aw = width - l.m * 2;
            l.panelX = l.m;
            l.panelY = Spacing.S2;
            l.panelW = l.aw;
            l.panelH = height - Spacing.S4;

            l.closeX = width - 18;
            l.closeY = l.panelY + 4;
            l.closeW = 12;
            l.closeH = 12;

            l.searchY = Spacing.S6 + Spacing.S1;
            l.searchH = BTN_H;
            l.searchX = l.panelX + Spacing.S2;
            l.searchW = l.panelW - Spacing.S4;
            int gap = BTN_GAP;
            l.goW = 44;
            l.folderW = 64;
            l.fieldX = l.searchX;
            l.fieldW = l.searchW - l.goW - l.folderW - gap * 2;
            l.goX = l.fieldX + l.fieldW + gap;
            l.folderX = l.goX + l.goW + gap;

            l.colTop = l.searchY + l.searchH + Spacing.S3;
            l.colBottom = l.panelY + l.panelH - Spacing.S2;
            int colGap = Spacing.S3;
            int inner = l.panelW - Spacing.S4;

            // Library & playback behavior strip between the search row and the columns.
            l.libX = l.panelX + Spacing.S2;
            l.libW = l.panelW - Spacing.S4;
            l.libY = l.colTop;
            l.libH = 56;
            l.libRowA = l.libY + 4;
            l.libRowB = l.libY + 28;
            l.setH = BTN_H;
            l.setW = 42;
            l.loadH = BTN_H;
            l.loadW = 50;
            l.loadX = l.libX + l.libW - l.loadW;
            l.setX = l.loadX - l.setW - BTN_GAP;
            l.setY = l.libRowA;
            l.loadY = l.libRowA;
            l.colTop2 = l.libY + l.libH + Spacing.S2;

            l.leftW = (int) ((inner - colGap) * 0.56);
            l.rightW = inner - colGap - l.leftW;
            l.leftX = l.panelX + Spacing.S2;
            l.rightX = l.leftX + l.leftW + colGap;

            l.resX = l.leftX;
            l.resY = l.colTop2;
            l.resW = l.leftW;
            l.resH = (l.colBottom - l.colTop2) - Spacing.S1;
            l.resRowH = 22;
            l.resHeaderH = 22;

            l.npX = l.rightX;
            l.npY = l.colTop2;
            l.npW = l.rightW;
            l.npTitleY = l.npY + Spacing.S2;
            l.npBarY = l.npTitleY + Spacing.S3 + 9;
            l.npBarH = BAR_H;
            l.npBarX = l.npX + Spacing.S2;
            l.npBarW = l.npW - Spacing.S4;
            l.npControlsY = l.npBarY + l.npBarH + Spacing.S3;
            l.npBtnH = BTN_H;
            l.npBtnGap = BTN_GAP;
            l.npBtnW = (l.npW - Spacing.S4 - l.npBtnGap * 4) / 5;
            for (int i = 0; i < 5; i++) l.npBtnX[i] = l.npX + Spacing.S2 + i * (l.npBtnW + l.npBtnGap);

            l.shuffleX = l.npX + Spacing.S2;
            l.shuffleY = l.npControlsY + l.npBtnH + l.npBtnGap;
            l.shuffleW = l.npBtnW;
            l.shuffleH = l.npBtnH;

            l.volY = l.shuffleY + l.npBtnH + l.npBtnGap;
            l.vizY = l.volY + l.npBtnH + Spacing.S1;
            l.vizH = Spacing.S3;
            l.qY = l.vizY + l.vizH + Spacing.S2;
            l.qX = l.rightX;
            l.qW = l.rightW;
            l.qTop = l.qY + Spacing.S2 + 14;
            l.qBottom = l.colBottom;
            l.npH = l.qY - l.npY;
            return l;
        }
    }

    // ---- Hover helpers -----------------------------------------------------

    private float hoverValue(String key, boolean h, float delta) {
        Animated a = hover.computeIfAbsent(key, k -> new Animated(0f, 16f));
        Boolean prev = wasHover.get(key);
        if (h && (prev == null || !prev)) UiSounds.hover();
        wasHover.put(key, h);
        a.set(h ? 1f : 0f);
        a.tick(delta);
        return a.get();
    }

    private float rowHoverValue(Map<Integer, Animated> map, int idx, boolean h, float delta) {
        Animated a = map.computeIfAbsent(idx, k -> new Animated(0f, 16f));
        a.set(h ? 1f : 0f);
        a.tick(delta);
        return a.get();
    }

    // ---- Render -------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Theme.tick(delta);
        anim.update(delta);
        float open = anim.t();
        if (open < 0.01f && !anim.isClosing()) return;

        L l = L.build(width, height);
        int accent = Theme.getAnimatedAccent();

        anim.backdrop(g, width, height);

        int wy = anim.slideY(-12);
        g.pose().pushMatrix();
        g.pose().translate(0, wy);

        // Outer panel
        Panel.drawElevated(g, l.panelX, l.panelY, l.panelW, l.panelH,
            ColorUtil.withAlpha(Theme.BG_PANEL, (int) (220 * open)), Theme.ELEVATION_2);
        Panel.drawHollowRect(g, l.panelX, l.panelY, l.panelW, l.panelH, ColorUtil.withAlpha(Theme.BORDER, 160));
        g.fill(l.panelX + 3, l.panelY + 2, l.panelX + l.panelW - 3, l.panelY + 3, ColorUtil.withAlpha(accent, Theme.topStripAlpha));

        // Title + close
        g.centeredText(font, getTitle(), l.panelX + l.panelW / 2, l.panelY + 8, Theme.FOREGROUND);
        boolean closeHover = mx >= l.closeX && mx <= l.closeX + l.closeW && my >= l.closeY && my <= l.closeY + l.closeH;
        hoverValue("close", closeHover, delta);
        g.text(font, Component.literal("✕"), l.closeX, l.closeY, closeHover ? Theme.DESTRUCTIVE : Theme.MUTED_FOREGROUND);

        renderSearchBar(g, mx, my, l, accent, delta);
        renderLibraryBehavior(g, mx, my, l, accent, delta);
        renderResults(g, mx, my, l, accent, delta);
        renderNowPlaying(g, mx, my, l, accent, delta);

        if (statusTimer > 0 && !statusText.isEmpty() && !searchPending) {
            int sc = statusIsError ? Theme.DESTRUCTIVE : Theme.MUTED_FOREGROUND;
            g.text(font, Component.literal(statusText), l.panelX + Spacing.S2,
                l.panelY + l.panelH - Spacing.S2 - font.lineHeight, sc);
        }

        g.pose().popMatrix();
    }

    private void renderSearchBar(GuiGraphicsExtractor g, int mx, int my, L l, int accent, float delta) {
        // Field
        boolean fieldHover = mx >= l.fieldX && mx <= l.fieldX + l.fieldW && my >= l.searchY && my <= l.searchY + l.searchH;
        hoverValue("field", fieldHover, delta);
        int fieldBg = urlFocused ? Theme.PRIMARY_CONTAINER : Theme.BG_SURFACE;
        Panel.draw(g, l.fieldX, l.searchY, l.fieldW, l.searchH, ColorUtil.withAlpha(fieldBg, 200));
        int fieldBorder = urlFocused ? accent : Theme.BORDER_LIGHT;
        Panel.drawHollowRect(g, l.fieldX, l.searchY, l.fieldW, l.searchH, ColorUtil.withAlpha(fieldBorder, urlFocused ? 180 : 90));

        String placeholder = "Search or paste URL...";
        String display = urlText.isEmpty() ? placeholder : urlText.toString();
        int textColor = urlText.isEmpty() ? Theme.TEXT_FAINT : Theme.TEXT;
        g.text(font, Component.literal(display), l.fieldX + 4, l.searchY + 6, textColor);

        if (urlFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = l.fieldX + 4 + font.width(urlText.substring(0, Math.min(urlCursor, urlText.length())));
            g.fill(cx, l.searchY + 4, cx + 1, l.searchY + l.searchH - 4, Theme.PRIMARY);
        }

        drawActionButton(g, l.goX, l.searchY, l.goW, l.searchH, "Go", "go", mx, my, false, delta);
        drawActionButton(g, l.folderX, l.searchY, l.folderW, l.searchH, "Folder", "folder", mx, my, false, delta);
    }

    private void renderLibraryBehavior(GuiGraphicsExtractor g, int mx, int my, L l, int accent, float delta) {
        MusicPlayerPrefs prefs = MusicPlayerPrefs.get();

        // Row A: library folder path + Set/Load buttons.
        g.text(font, Component.literal("Library:"), l.libX, l.libRowA + 6, Theme.MUTED_FOREGROUND);
        String path = prefs.getLibraryFolder();
        boolean hasPath = !path.isBlank();
        String shown = hasPath ? path : "Not set — type a folder path into the search field and press Set";
        int maxW = l.libW - 52 - l.setW - l.loadW - BTN_GAP * 2 - 8;
        if (font.width(shown) > maxW && maxW > 0) shown = font.plainSubstrByWidth(shown, maxW - 4) + "...";
        g.text(font, Component.literal(shown), l.libX + 52, l.libRowA + 6,
            hasPath ? Theme.TEXT_DIM : Theme.TEXT_FAINT);

        libSetY = l.setY; libSetH = l.setH;
        libLoadY = l.loadY; libLoadH = l.loadH;
        drawActionButton(g, l.loadX, l.loadY, l.loadW, l.loadH, "Load", "library_load", mx, my, hasPath, delta);
        drawActionButton(g, l.setX, l.setY, l.setW, l.setH, "Set", "library_set", mx, my, false, delta);

        // Row B: behavior chips + duck level slider.
        String[] labels = {" Pause with game ", " Duck on pause ", " Restore queue ", notifyLabel(prefs.getNotifyMode())};
        boolean[] active = {prefs.isPauseWithGame(), prefs.isDucking(), prefs.isRestoreQueue(), false};
        int cx = l.libX;
        toggChipY = l.libRowB; toggChipH = BTN_H;
        for (int i = 0; i < 4; i++) {
            int cw = font.width(labels[i]) + 16;
            toggChipX[i] = cx; toggChipW[i] = cw;
            drawToggleChip(g, cx, toggChipY, cw, toggChipH, labels[i], active[i], accent, mx, my, "chip" + i, delta);
            cx += cw + BTN_GAP;
        }

        // Duck slider at the right end of the row (when there is room).
        int sliderEnd = l.libX + l.libW;
        int pctW = font.width("100%") + 2;
        int labW = font.width("Duck") + 6;
        int barAreaW = sliderEnd - cx - pctW;
        if (barAreaW >= labW + 24) {
            int trackX = cx + labW;
            int trackW = barAreaW - labW;
            int by = toggChipY + (toggChipH - BAR_H) / 2;
            g.text(font, Component.literal("Duck"), cx, toggChipY + 6, Theme.MUTED_FOREGROUND);
            g.fill(trackX, by, trackX + trackW, by + BAR_H, Theme.MUTED);
            float v = prefs.getDuckVolume() / 100f;
            int fillW = Math.max(BAR_H, (int) (trackW * v));
            g.fill(trackX, by, trackX + fillW, by + BAR_H, accent);
            int kx = trackX + (int) (trackW * v) - BAR_H / 2;
            g.fill(kx, by - 1, kx + BAR_H, by + BAR_H + 1, Theme.FOREGROUND);
            duckBarX = trackX; duckBarW = trackW; duckBarY = by;
            String pct = (int) prefs.getDuckVolume() + "%";
            g.text(font, Component.literal(pct), sliderEnd - pctW + Math.max(0, (pctW - font.width(pct)) / 2), toggChipY + 6, Theme.TEXT_DIM);
        } else {
            duckBarW = 0;
            g.text(font, Component.literal("Duck " + (int) prefs.getDuckVolume() + "%"), cx, toggChipY + 6, Theme.TEXT_DIM);
        }
    }

    private void drawToggleChip(GuiGraphicsExtractor g, int x, int y, int w, int h, String label,
                                boolean active, int accent, int mx, int my, String key, float delta) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        float hv = hoverValue(key, hover, delta);
        int base = active ? ColorUtil.withAlpha(Theme.PRIMARY, 170) : ColorUtil.withAlpha(Theme.SURFACE_VARIANT, 200);
        int hov = ColorUtil.withAlpha(Theme.BG_HOVER, 220);
        Panel.draw(g, x, y, w, h, ColorUtil.lerpARGB(base, hov, hv));
        int border = active ? accent : ColorUtil.lerpARGB(Theme.BORDER_LIGHT, accent, hv);
        Panel.drawHollowRect(g, x, y, w, h, ColorUtil.withAlpha(border, active ? 170 : (hover ? 200 : 90)));
        int fg = active ? Theme.PRIMARY_FOREGROUND : (hover ? Theme.ON_SURFACE : Theme.ON_SURFACE_VARIANT);
        g.centeredText(font, Component.literal(label), x + w / 2, y + (h - font.lineHeight) / 2, fg);
    }

    private void drawActionButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label,
                                  String key, int mx, int my, boolean active, float delta) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        float hv = hoverValue(key, hover, delta);
        int base = active ? ColorUtil.withAlpha(Theme.PRIMARY, 180) : ColorUtil.withAlpha(Theme.SURFACE_VARIANT, 220);
        int hov = ColorUtil.withAlpha(Theme.BG_HOVER, 220);
        Panel.draw(g, x, y, w, h, ColorUtil.lerpARGB(base, hov, hv));
        int border = ColorUtil.lerpARGB(Theme.BORDER_LIGHT, Theme.getAnimatedAccent(), hv);
        Panel.drawHollowRect(g, x, y, w, h, ColorUtil.withAlpha(border, hover ? 200 : 90));
        int fg = hover ? Theme.ON_SURFACE : Theme.ON_SURFACE_VARIANT;
        g.centeredText(font, Component.literal(label), x + w / 2, y + (h - font.lineHeight) / 2, fg);
    }

    private void renderResults(GuiGraphicsExtractor g, int mx, int my, L l, int accent, float delta) {
        if (searchPending) {
            String dots = ".".repeat((int) ((System.currentTimeMillis() / 400) % 4));
            g.text(font, Component.literal("Searching" + dots), l.resX + Spacing.S1, l.resY + Spacing.S1, accent);
            return;
        }
        if (searchResults == null || searchResults.isEmpty()) return;

        Panel.drawGlassCard(g, l.resX, l.resY, l.resW, l.resH, false);

        String header = "Results (" + searchResults.size() + ")";
        g.text(font, Component.literal(header), l.resX + Spacing.S2, l.resY + Spacing.S1, accent);
        g.fill(l.resX + Spacing.S2, l.resY + l.resHeaderH - 2, l.resX + l.resW - Spacing.S2, l.resY + l.resHeaderH - 1,
            ColorUtil.withAlpha(Theme.BORDER, 120));

        int rowH = l.resRowH;
        int maxRows = Math.min(searchResults.size(), Math.max(3, (l.resH - l.resHeaderH - Spacing.S2) / rowH));
        int ry = l.resY + l.resHeaderH + Spacing.S1;
        int maxTextW = l.resW - 68;
        int bottom = l.resY + l.resH - Spacing.S1;

        g.enableScissor(l.resX, l.resY, l.resX + l.resW, l.resY + l.resH);
        for (int i = 0; i < maxRows; i++) {
            int idx = i + resultScroll;
            if (idx >= searchResults.size() || ry >= bottom) break;
            AudioTrack track = searchResults.get(idx);
            boolean ho = my >= ry && my <= ry + rowH && mx >= l.resX + Spacing.S1 && mx <= l.resX + l.resW - Spacing.S1;
            boolean se = idx == selectedResult;
            float hv = rowHoverValue(resHover, idx, ho, delta);

            if (se) {
                g.fill(l.resX + Spacing.S2, ry, l.resX + l.resW - Spacing.S2, ry + rowH, ColorUtil.withAlpha(Theme.BG_SELECT, 100));
            } else if (ho) {
                g.fill(l.resX + Spacing.S2, ry, l.resX + l.resW - Spacing.S2, ry + rowH, ColorUtil.withAlpha(Theme.BG_HOVER, (int) (120 * hv)));
            }

            g.text(font, Component.literal("▶"), l.resX + Spacing.S2 + 4, ry + 5, ho ? accent : Theme.MUTED_FOREGROUND);

            String text = sanitizeMeta(track.getInfo().title);
            if (track.getInfo().author != null && !track.getInfo().author.equals("Unknown")) {
                text += " - " + sanitizeMeta(track.getInfo().author);
            }
            if (font.width(text) > maxTextW) text = font.plainSubstrByWidth(text, maxTextW - 4) + "...";
            g.text(font, Component.literal(text), l.resX + Spacing.S2 + 18, ry + 5, ho ? Theme.TEXT : Theme.TEXT_DIM);

            String dur = formatTime(track.getDuration());
            g.text(font, Component.literal(dur), l.resX + l.resW - Spacing.S2 - font.width(dur) - 4, ry + 5, Theme.TEXT_FAINT);
            ry += rowH;
        }
        g.disableScissor();

        if (searchResults.size() > maxRows) {
            int visible = Math.min(maxRows, searchResults.size() - resultScroll);
            String info = (resultScroll + 1) + "-" + Math.min(resultScroll + visible, searchResults.size()) + "/" + searchResults.size();
            g.text(font, Component.literal(info), l.resX + l.resW - Spacing.S2 - font.width(info), l.resY + l.resH - 12, Theme.TEXT_FAINT);
        }
    }

    private void renderNowPlaying(GuiGraphicsExtractor g, int mx, int my, L l, int accent, float delta) {
        Panel.drawGlassCard(g, l.npX, l.npY, l.npW, l.npH, false);

        if (!player.hasTrack()) {
            g.text(font, Component.literal("NOW PLAYING"), l.npX + Spacing.S2, l.npTitleY, Theme.MUTED_FOREGROUND);
            int ny = l.npTitleY + Spacing.S3 + font.lineHeight;
            g.text(font, Component.literal("No track loaded"), l.npX + Spacing.S2, ny, Theme.TEXT_DIM);
            if (!player.isBackendAvailable()) {
                g.text(font, Component.literal("⚠ No audio device available for playback"), l.npX + Spacing.S2, ny + Spacing.S3, Theme.DESTRUCTIVE);
            }
            return;
        }

        var info = player.getCurrentTrack().getInfo();
        String title = info.title != null ? sanitizeMeta(info.title) : "Unknown";
        String author = info.author != null ? sanitizeMeta(info.author) : "Unknown";
        String label = "♫  " + title + " — " + author;
        if (font.width(label) > l.npW - Spacing.S4) label = font.plainSubstrByWidth(label, l.npW - Spacing.S4 - 6) + "...";
        g.text(font, Component.literal("NOW PLAYING"), l.npX + Spacing.S2, l.npTitleY - 4, Theme.MUTED_FOREGROUND);
        g.text(font, Component.literal(label), l.npX + Spacing.S2, l.npTitleY + 8, Theme.TEXT);

        // Seek bar (eased)
        long pos = player.getPosition();
        long dur = player.getDuration();
        progressAnim.set(dur > 0 ? (float) pos / dur : 0f);
        progressAnim.tick(delta);
        float prog = progressAnim.get();
        nowPlayingBarX = l.npBarX;
        nowPlayingBarY = l.npBarY;
        nowPlayingBarW = l.npBarW;
        boolean hp = mx >= l.npBarX && mx <= l.npBarX + l.npBarW && my >= l.npBarY - 3 && my <= l.npBarY + l.npBarH + 3;
        g.fill(l.npBarX, l.npBarY, l.npBarX + l.npBarW, l.npBarY + l.npBarH, Theme.MUTED);
        int fillW = Math.max(BAR_H, (int) (l.npBarW * prog));
        g.fill(l.npBarX, l.npBarY, l.npBarX + fillW, l.npBarY + l.npBarH, accent);
        // knob
        int kx = l.npBarX + (int) (l.npBarW * prog) - BAR_H / 2;
        g.fill(kx, l.npBarY - 1, kx + BAR_H, l.npBarY + l.npBarH + 1, hp ? Theme.FOREGROUND : ColorUtil.withAlpha(Theme.FOREGROUND, 220));
        g.text(font, Component.literal(formatTime(pos) + " / " + formatTime(dur)), l.npBarX, l.npBarY + l.npBarH + 3, Theme.TEXT_FAINT);

        // Transport
        drawTransport(g, l, accent, "prev", 0, "⏮", false, mx, my, delta);
        drawTransport(g, l, accent, "play", 1, player.isPaused() ? "▶" : "⏸", false, mx, my, delta);
        drawTransport(g, l, accent, "stop", 2, "⏹", false, mx, my, delta);
        drawTransport(g, l, accent, "next", 3, "⏭", false, mx, my, delta);
        String repLabel = switch (player.getRepeatMode()) {
            case OFF -> "↻";
            case ALL -> "↻ All";
            case ONE -> "↻ 1";
        };
        drawTransport(g, l, accent, "repeat", 4, repLabel, player.getRepeatMode() != MusicPlayer.RepeatMode.OFF, mx, my, delta);

        drawTransport(g, l, accent, "shuffle", -1, "⤮ Shuffle", player.isShuffle(), mx, my, delta);
        shuffleBtnX = l.shuffleX; shuffleBtnY = l.shuffleY; shuffleBtnW = l.shuffleW; shuffleBtnH = l.shuffleH;

        // Volume (eased)
        renderVolumeSlider(g, mx, my, l, accent, delta);

        // Visualizer
        renderVisualizer(g, l, accent, delta);

        // Queue
        if (l.qBottom - l.qTop > 40) renderQueue(g, mx, my, l, accent, delta);
    }

    private void drawTransport(GuiGraphicsExtractor g, L l, int accent, String key, int idx, String label, boolean active, int mx, int my, float delta) {
        int x = idx < 0 ? l.shuffleX : l.npBtnX[idx];
        int y = idx < 0 ? l.shuffleY : l.npControlsY;
        int w = idx < 0 ? l.shuffleW : l.npBtnW;
        int h = l.npBtnH;
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        float hv = hoverValue(key, hover, delta);
        int base = active ? ColorUtil.withAlpha(Theme.PRIMARY, 180) : ColorUtil.withAlpha(Theme.SURFACE_VARIANT, 200);
        int hov = ColorUtil.withAlpha(Theme.BG_HOVER, 220);
        Panel.draw(g, x, y, w, h, ColorUtil.lerpARGB(base, hov, hv));
        int border = active ? accent : ColorUtil.lerpARGB(Theme.BORDER_LIGHT, accent, hv);
        Panel.drawHollowRect(g, x, y, w, h, ColorUtil.withAlpha(border, active ? 170 : (hover ? 200 : 90)));
        int fg = active ? Theme.PRIMARY_FOREGROUND : (hover ? Theme.ON_SURFACE : Theme.ON_SURFACE_VARIANT);
        g.centeredText(font, Component.literal(label), x + w / 2, y + (h - font.lineHeight) / 2, fg);
    }

    private void renderVolumeSlider(GuiGraphicsExtractor g, int mx, int my, L l, int accent, float delta) {
        int labelW = font.width("Volume") + 6;
        int pctW = font.width(" 100%") + 4;
        int vsW = l.npW - Spacing.S4 - labelW - pctW;
        if (vsW < 40) vsW = 40;
        int barX = l.npX + Spacing.S2 + labelW + 2;
        int barY = l.volY + (l.npBtnH - BAR_H) / 2;
        volBarX = barX; volBarW = vsW; volBarY = barY;
        g.text(font, Component.literal("Volume"), l.npX + Spacing.S2, l.volY + 6, Theme.MUTED_FOREGROUND);

        volumeAnim.set(player.getSliderVolume() / 100f);
        volumeAnim.tick(delta);
        float vol = volumeAnim.get();
        boolean hoverVol = mx >= barX && mx <= barX + vsW && my >= barY - 3 && my <= barY + BAR_H + 3;
        hoverValue("vol", hoverVol, delta);

        g.fill(barX, barY, barX + vsW, barY + BAR_H, Theme.MUTED);
        int vf = Math.max(BAR_H, (int) (vsW * vol));
        g.fill(barX, barY, barX + vf, barY + BAR_H, accent);
        int kx = barX + (int) (vsW * vol) - BAR_H / 2;
        g.fill(kx, barY - 1, kx + BAR_H, barY + BAR_H + 1, hoverVol ? Theme.FOREGROUND : ColorUtil.withAlpha(Theme.FOREGROUND, 220));

        String pct = (int) (player.getSliderVolume()) + "%";
        g.text(font, Component.literal(pct), barX + vsW + 4, l.volY + 6, hoverVol ? Theme.TEXT : Theme.TEXT_DIM);
    }

    private void renderVisualizer(GuiGraphicsExtractor g, L l, int accent, float delta) {
        int n = VIZ_BARS;
        int gap = 3;
        int bw = Math.min(6, (l.npW - Spacing.S4) / (n * 2));
        int totalW = n * (bw + gap) - gap;
        int startX = l.npX + Spacing.S2;
        double time = System.currentTimeMillis() / 1000.0;
        boolean playing = player.isPlaying();
        for (int i = 0; i < n; i++) {
            double ph = time * (2.0 + i * 0.4);
            float f = (float) (Math.sin(ph) * 0.5 + 0.5);
            int h = playing ? (int) (5 + f * (l.vizH - 5)) : 4;
            int bx = startX + i * (bw + gap);
            int by = l.vizY + (l.vizH - h) / 2;
            g.fill(bx, by, bx + bw, by + h, ColorUtil.withAlpha(accent, playing ? 200 : 110));
        }
    }

    private void renderQueue(GuiGraphicsExtractor g, int mx, int my, L l, int accent, float delta) {
        Panel.draw(g, l.qX, l.qY, l.qW, l.qBottom - l.qY, ColorUtil.withAlpha(Theme.SURFACE, 80));
        Panel.drawHollowRect(g, l.qX, l.qY, l.qW, l.qBottom - l.qY, ColorUtil.withAlpha(Theme.BORDER_LIGHT, 90));

        List<AudioTrack> q = player.getQueue();
        g.text(font, Component.literal("QUEUE (" + q.size() + ")"), l.qX + Spacing.S2, l.qY + Spacing.S1, Theme.MUTED_FOREGROUND);
        g.fill(l.qX + Spacing.S2, l.qTop - 4, l.qX + l.qW - Spacing.S2, l.qTop - 3, ColorUtil.withAlpha(Theme.BORDER, 120));

        int listY = l.qTop;
        int listH = l.qBottom - listY;
        if (listH <= 0) return;

        int rowH = queueRowH;
        int rows = Math.max(1, listH / rowH);
        int total = q.size();
        int maxScroll = Math.max(0, total - rows);
        if (queueScroll > maxScroll) queueScroll = maxScroll;
        if (queueScroll < 0) queueScroll = 0;
        queueRows = rows;

        int x = l.qX + Spacing.S2;
        int w = l.qW - Spacing.S4;
        int maxTextW = w - 40;
        int cur = player.getQueueIndex();

        g.enableScissor(l.qX, l.qTop - 2, l.qX + l.qW, l.qBottom);
        for (int i = 0; i < rows; i++) {
            int idx = i + queueScroll;
            if (idx >= total) break;
            AudioTrack track = q.get(idx);
            int ry = listY + i * rowH;
            boolean ho = my >= ry && my <= ry + rowH && mx >= x && mx <= x + w;
            boolean playing = idx == cur;
            float hv = rowHoverValue(qHover, idx, ho, delta);

            if (playing) {
                g.fill(x, ry, x + w, ry + rowH, ColorUtil.withAlpha(accent, 40));
                g.fill(x, ry, x + 2, ry + rowH, accent);
            } else if (ho) {
                g.fill(x, ry, x + w, ry + rowH, ColorUtil.withAlpha(Theme.BG_HOVER, (int) (120 * hv)));
            }

            String text = (idx + 1) + ". " + sanitizeMeta(track.getInfo().title);
            if (track.getInfo().author != null && !track.getInfo().author.equals("Unknown")) {
                text += " - " + sanitizeMeta(track.getInfo().author);
            }
            if (font.width(text) > maxTextW) text = font.plainSubstrByWidth(text, maxTextW - 4) + "...";
            int col = playing ? Theme.FOREGROUND : (ho ? Theme.TEXT : Theme.TEXT_DIM);
            g.text(font, Component.literal(text), x + 6, ry + 5, col);
        }
        g.disableScissor();

        if (total > rows) {
            float thumbH = (float) rows / total * (l.qBottom - l.qTop);
            float thumbY = (l.qTop) + (queueScroll / (float) Math.max(1, maxScroll)) * ((l.qBottom - l.qTop) - thumbH);
            g.fill(x + w - 2, (int) thumbY, x + w, (int) (thumbY + thumbH), ColorUtil.withAlpha(accent, 160));
        }
    }

    // --- Input ---------------------------------------------------------------

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!urlFocused) return false;
        int cp = event.codepoint();
        if (cp >= 32 && cp < 127) {
            urlText.insert(Math.min(urlCursor, urlText.length()), String.valueOf((char) cp));
            urlCursor = Math.min(urlCursor + 1, urlText.length());
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }

        if (urlFocused) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { goAction(); return true; }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (urlCursor > 0 && urlText.length() > 0) { urlText.deleteCharAt(urlCursor - 1); urlCursor--; }
                return true;
            }
            if (key == GLFW.GLFW_KEY_DELETE) { if (urlCursor < urlText.length()) urlText.deleteCharAt(urlCursor); return true; }
            if (key == GLFW.GLFW_KEY_LEFT && urlCursor > 0) { urlCursor--; return true; }
            if (key == GLFW.GLFW_KEY_RIGHT && urlCursor < urlText.length()) { urlCursor++; return true; }
            if (key == GLFW.GLFW_KEY_HOME) { urlCursor = 0; return true; }
            if (key == GLFW.GLFW_KEY_END) { urlCursor = urlText.length(); return true; }
            if (key == GLFW.GLFW_KEY_V && (event.modifiers() & 2) != 0) {
                String clip = glfwGetClipboardString(minecraft.getWindow().handle());
                if (clip != null) {
                    clip = sanitizeInput(clip);
                    if (!clip.isEmpty()) { urlText.insert(urlCursor, clip); urlCursor += clip.length(); }
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_DOWN && hasResults() && !searchResults.isEmpty()) {
                urlFocused = false; selectedResult = 0; return true;
            }
            return true;
        }

        if (hasResults()) {
            if (key == GLFW.GLFW_KEY_UP) {
                if (selectedResult <= 0) { urlFocused = true; selectedResult = -1; return true; }
                selectedResult--; if (selectedResult < resultScroll) resultScroll = selectedResult; return true;
            }
            if (key == GLFW.GLFW_KEY_DOWN) {
                if (selectedResult < 0) selectedResult = 0;
                else if (selectedResult < searchResults.size() - 1) selectedResult++;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (selectedResult >= 0 && selectedResult < searchResults.size()) { playResult(selectedResult); return true; }
            }
        }

        if (key == GLFW.GLFW_KEY_SPACE) {
            urlFocused = false; selectedResult = -1;
            if (player.hasTrack()) { player.togglePause(); return true; }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int btn = event.buttonInfo().input();
        if (btn != 0) return super.mouseClicked(event, doubleClick);

        double mx = event.x();
        double my = event.y();
        L l = L.build(width, height);

        if (mx >= l.closeX && mx <= l.closeX + l.closeW && my >= l.closeY && my <= l.closeY + l.closeH) {
            onClose(); return true;
        }

        if (mx >= l.fieldX && mx <= l.fieldX + l.fieldW && my >= l.searchY && my <= l.searchY + l.searchH) {
            urlFocused = true; selectedResult = -1;
            urlCursor = font.plainSubstrByWidth(urlText.toString(), Math.max(0, (int) mx - l.fieldX - 4)).length();
            return true;
        }
        if (mx >= l.goX && mx <= l.goX + l.goW && my >= l.searchY && my <= l.searchY + l.searchH) { UiSounds.click(); goAction(); return true; }
        if (mx >= l.folderX && mx <= l.folderX + l.folderW && my >= l.searchY && my <= l.searchY + l.searchH) { UiSounds.click(); loadFolderAction(); return true; }
        urlFocused = false;

        // Library strip
        if (mx >= l.setX && mx <= l.setX + l.setW && my >= libSetY && my <= libSetY + libSetH) {
            UiSounds.click();
            String path = urlText.toString().trim();
            if (path.isEmpty()) {
                setStatus("Type a folder path into the search field first");
            } else {
                MusicPlayerPrefs.get().setLibraryFolder(path);
                setStatus("Library set: " + path);
            }
            return true;
        }
        if (mx >= l.loadX && mx <= l.loadX + l.loadW && my >= libLoadY && my <= libLoadY + libLoadH) {
            UiSounds.click(); loadLibraryAction(); return true;
        }
        for (int i = 0; i < 4; i++) {
            if (toggChipW[i] > 0 && mx >= toggChipX[i] && mx <= toggChipX[i] + toggChipW[i]
                && my >= toggChipY && my <= toggChipY + toggChipH) {
                UiSounds.click();
                MusicPlayerPrefs p = MusicPlayerPrefs.get();
                switch (i) {
                    case 0 -> { p.setPauseWithGame(!p.isPauseWithGame()); setStatus("Pause with game: " + p.isPauseWithGame()); }
                    case 1 -> { p.setDucking(!p.isDucking()); setStatus("Duck on pause: " + p.isDucking()); }
                    case 2 -> { p.setRestoreQueue(!p.isRestoreQueue()); setStatus("Restore queue: " + p.isRestoreQueue()); }
                    case 3 -> { p.setNotifyMode(p.getNotifyMode() + 1); setStatus("Now-playing notify: " + notifyLabel(p.getNotifyMode())); }
                }
                MusicModule.refreshIdleState();
                return true;
            }
        }
        if (duckBarW > 0 && mx >= duckBarX - 2 && mx <= duckBarX + duckBarW + 2 && my >= duckBarY - 4 && my <= duckBarY + BAR_H + 4) {
            UiSounds.click();
            float pct = (float) ((mx - duckBarX) / Math.max(1, duckBarW));
            MusicPlayerPrefs.get().setDuckVolume(Mth.clamp(pct * 100, 0, 100));
            draggingDuck = true;
            return true;
        }

        // Results
        if (hasResults()) {
            int rowH = l.resRowH;
            int maxRows = Math.min(searchResults.size(), Math.max(3, (l.resH - l.resHeaderH - Spacing.S2) / rowH));
            int ry = l.resY + l.resHeaderH + Spacing.S1;
            for (int i = 0; i < maxRows && i + resultScroll < searchResults.size(); i++) {
                int idx = i + resultScroll;
                if (mx >= l.resX + Spacing.S1 && mx <= l.resX + l.resW - Spacing.S1 && my >= ry && my <= ry + rowH) {
                    UiSounds.click(); playResult(idx); return true;
                }
                ry += rowH;
            }
        }

        if (player.hasTrack()) {
            // Transport
            for (int i = 0; i < 5; i++) {
                int x = l.npBtnX[i], y = l.npControlsY, w = l.npBtnW, h = l.npBtnH;
                if (mx >= x && mx <= x + w && my >= y && my <= y + h) {
                    UiSounds.click();
                    switch (i) {
                        case 0 -> player.previous();
                        case 1 -> player.togglePause();
                        case 2 -> { player.stop(); setStatus("Stopped"); }
                        case 3 -> player.next();
                        case 4 -> { player.cycleRepeatMode(); MusicModule.syncPrefs(); }
                    }
                    return true;
                }
            }
            if (mx >= l.shuffleX && mx <= l.shuffleX + l.shuffleW && my >= l.shuffleY && my <= l.shuffleY + l.shuffleH) {
                UiSounds.click(); player.setShuffle(!player.isShuffle()); MusicModule.syncPrefs(); return true;
            }

            // Seek (click + begin drag)
            if (nowPlayingBarW > 0 && mx >= nowPlayingBarX - 2 && mx <= nowPlayingBarX + nowPlayingBarW + 2
                && my >= nowPlayingBarY - 4 && my <= nowPlayingBarY + l.npBarH + 4) {
                float pct = Mth.clamp((float) (mx - nowPlayingBarX) / nowPlayingBarW, 0, 1);
                player.seek((long) (player.getDuration() * pct));
                draggingProgress = true;
                return true;
            }

            // Volume
            int labelW = font.width("Volume") + 6;
            int pctW = font.width(" 100%") + 4;
            int vsW = l.npW - Spacing.S4 - labelW - pctW;
            if (vsW < 40) vsW = 40;
            int barX = l.npX + Spacing.S2 + labelW + 2;
            int barY = l.volY + (l.npBtnH - BAR_H) / 2;
            if (mx >= barX - 2 && mx <= barX + vsW + 2 && my >= barY - 4 && my <= barY + BAR_H + 4) {
                UiSounds.click();
                float pct = (float) ((mx - barX) / Math.max(1, vsW));
                player.setSliderVolume(Mth.clamp(pct * 100, 0, 100));
                draggingVolume = true;
                return true;
            }

            // Queue jump
            if (queueRows > 0 && mx >= l.qX + Spacing.S1 && mx <= l.qX + l.qW - Spacing.S1
                && my >= l.qTop && my <= l.qTop + queueRows * queueRowH) {
                int row = (int) ((my - l.qTop) / queueRowH);
                int idx = row + queueScroll;
                if (idx >= 0 && idx < player.getQueue().size()) {
                    UiSounds.click(); player.playQueueItem(idx); return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        L l = L.build(width, height);
        if (hasResults() && mouseX >= l.resX && mouseX <= l.resX + l.resW && mouseY >= l.resY && mouseY <= l.resY + l.resH) {
            int maxRows = Math.max(3, (l.resH - l.resHeaderH - Spacing.S2) / l.resRowH);
            int maxScroll = Math.max(0, searchResults.size() - maxRows);
            resultScroll = Mth.clamp(resultScroll - (int) deltaY, 0, maxScroll);
            return true;
        }
        if (player.hasTrack() && queueRows > 0 && mouseX >= l.qX && mouseX <= l.qX + l.qW
            && mouseY >= l.qTop - 2 && mouseY <= l.qBottom) {
            int total = player.getQueue().size();
            int maxScroll = Math.max(0, total - queueRows);
            queueScroll = Mth.clamp(queueScroll - (int) deltaY, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double mx = event.x();
        if (draggingVolume && player.hasTrack() && volBarW > 0) {
            float pct = (float) ((mx - volBarX) / volBarW);
            player.setSliderVolume(Mth.clamp(pct * 100, 0, 100));
            return true;
        }
        if (draggingProgress && player.hasTrack() && nowPlayingBarW > 0) {
            float pct = Mth.clamp((float) (mx - nowPlayingBarX) / nowPlayingBarW, 0, 1);
            player.seek((long) (player.getDuration() * pct));
            return true;
        }
        if (draggingDuck && duckBarW > 0) {
            float pct = (float) ((mx - duckBarX) / duckBarW);
            MusicPlayerPrefs.get().setDuckVolume(Mth.clamp(pct * 100, 0, 100));
            MusicModule.refreshIdleState();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingVolume = false;
        draggingProgress = false;
        draggingDuck = false;
        MusicModule.syncPrefs();
        return super.mouseReleased(event);
    }

    @Override
    public void tick() {
        super.tick();
        if (statusTimer > 0) statusTimer--;
    }

    @Override
    public void onClose() {
        anim.closeTo(backScreen);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean isCapturingText() { return urlFocused; }

    private boolean hasResults() { return searchResults != null && !searchResults.isEmpty(); }

    private void goAction() {
        String input = urlText.toString().trim();
        if (input.isEmpty()) return;
        searchResults = null; resultScroll = 0; selectedResult = -1;
        if (input.contains("://")) {
            setStatus("Loading...");
            player.loadAndPlay(input);
        } else {
            searchPending = true;
            setStatus("Searching...");
            player.search(input, new MusicPlayer.SearchCallback() {
                @Override
                public void onResults(List<AudioTrack> tracks) {
                    Minecraft.getInstance().execute(() -> {
                        searchResults = tracks;
                        searchPending = false;
                        setStatus("Found " + tracks.size() + " result" + (tracks.size() != 1 ? "s" : ""));
                    });
                }
                @Override
                public void onError(String msg) {
                    Minecraft.getInstance().execute(() -> {
                        searchPending = false;
                        statusIsError = true;
                        setStatus(msg);
                    });
                }
            });
        }
    }

    private void loadFolderAction() {
        String input = urlText.toString().trim();
        if (input.isEmpty()) input = MusicPlayerPrefs.get().getLibraryFolder();
        if (input.isEmpty()) { setStatus("Paste a folder path (or set your library)"); return; }
        setStatus("Loading folder...");
        player.loadLocalFolder(input);
    }

    private void loadLibraryAction() {
        String dir = MusicPlayerPrefs.get().getLibraryFolder();
        if (dir.isBlank()) { setStatus("No library set — type a path into the search field and press Set"); return; }
        setStatus("Loading library: " + dir);
        player.loadLocalFolder(dir);
    }

    private void playResult(int index) {
        if (index < 0 || index >= searchResults.size()) return;
        AudioTrack track = searchResults.get(index);
        selectedResult = index;
        setStatus("▶  Loading: " + track.getInfo().title);
        List<AudioTrack> clone = new java.util.ArrayList<>();
        for (AudioTrack t : searchResults) clone.add(t.makeClone());
        player.playList(clone, index);
    }

    public void setStatus(String msg) { statusText = msg; statusTimer = 80; }

    private static String notifyLabel(int mode) {
        return switch (mode) {
            case 1 -> " Notify: Chat ";
            case 2 -> " Notify: Off ";
            default -> " Notify: Toast ";
        };
    }

    private static String formatTime(long ms) {
        long sec = ms / 1000;
        if (sec >= 3600) return String.format("%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    private static String sanitizeInput(String s) {
        if (s == null) return "";
        var sb = new StringBuilder(Math.min(s.length(), 4096));
        for (int i = 0; i < s.length() && sb.length() < 4096; i++) {
            char c = s.charAt(i);
            if (c == '\t' || c == ' ') sb.append(c);
            else if (c >= 32 && c < 127) sb.append(c);
        }
        return sb.toString().trim();
    }

    private static String sanitizeMeta(String s) {
        if (s == null) return "";
        var sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 && c != '\t' && c != '\n' && c != '\r') continue;
            sb.append(c);
        }
        return sb.toString();
    }
}
