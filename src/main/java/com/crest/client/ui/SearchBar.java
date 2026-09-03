package com.crest.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class SearchBar implements Widget {
    private String text = "";
    private final Consumer<String> onChange;
    private final String placeholder;
    private boolean focused;
    private float cursorTimer;
    private int lastX, lastY, lastW;

    public SearchBar(Consumer<String> onChange) {
        this(onChange, "Search...");
    }

    public SearchBar(Consumer<String> onChange, String placeholder) {
        this.onChange = onChange;
        this.placeholder = placeholder;
    }

    public String getText() { return text; }

    /** Advance time-based state (cursor blink). Call once per frame with real dt. */
    public void tick(float delta) {
        cursorTimer += delta;
    }

    public boolean isFocused() { return focused; }

    public void setText(String text) {
        this.text = text;
    }

    public void clear() {
        text = "";
        if (onChange != null) onChange.accept(text);
    }

    @Override
    public int getWidth() {
        return lastW;
    }

    public int getHeight() { return 36; }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mx, int my, float delta) {
        lastX = x; lastY = y; lastW = w;

        int sbH = getHeight();
        g.fill(x, y, x + w, y + sbH, ColorUtil.withAlpha(Theme.SIDEBAR_BG, focused ? 230 : 190));
        Panel.drawHollowRect(g, x, y, w, sbH, focused ? Theme.getAnimatedAccent() : Theme.BORDER_LIGHT);

        String display = text.isEmpty() ? placeholder : text;
        int fg = text.isEmpty() ? Theme.MUTED_FOREGROUND : Theme.FOREGROUND;
        g.text(font, Component.literal(display), x + 15, y + 12, fg);

        if (!text.isEmpty()) {
            int cx = x + w - 22, cy = y + 8;
            boolean ch = mx >= cx && mx <= cx + 16 && my >= cy && my <= cy + 16;
            g.fill(cx, cy, cx + 16, cy + 16, ch ? ColorUtil.withAlpha(Theme.getAnimatedAccent(), 60) : 0);
            g.text(font, Component.literal("\u00D7"), cx + 4, cy + 3, Theme.FOREGROUND);
        }

        if (focused && text.isEmpty() && (int) (cursorTimer * 2) % 2 == 0) {
            int cx = x + 15 + font.width(placeholder);
            g.fill(cx, y + 10, cx + 1, y + 24, Theme.PRIMARY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;

        if (mx < lastX || mx > lastX + lastW || my < lastY || my > lastY + getHeight()) {
            focused = false;
            return false;
        }

        if (!text.isEmpty()) {
            int cx = lastX + lastW - 22, cy = lastY + 8;
            if (mx >= cx && mx <= cx + 16 && my >= cy && my <= cy + 16) {
                clear();
                return true;
            }
        }

        focused = true;
        cursorTimer = 0;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!focused) return false;

        if (key == 259 && !text.isEmpty()) { // Backspace
            text = text.substring(0, text.length() - 1);
            if (onChange != null) onChange.accept(text);
            return true;
        }
        if (key == 257 || key == 335) { // Enter
            focused = false;
            return true;
        }
        if (key == 256) { // Escape
            focused = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(int codepoint, int mods) {
        if (!focused) return false;
        if (codepoint >= 32 && codepoint < 127) {
            text += (char) codepoint;
            if (onChange != null) onChange.accept(text);
            return true;
        }
        return false;
    }

    public void blur() {
        focused = false;
    }

    public static boolean fuzzyMatch(String query, String target) {
        if (query == null || query.isEmpty()) return true;
        if (target == null) return false;
        String lq = query.toLowerCase();
        String lt = target.toLowerCase();
        if (lt.contains(lq)) return true;
        return levenshtein(lt, lq) <= Math.max(1, lq.length() / 3);
    }

    private static int levenshtein(String a, String b) {
        int m = a.length(), n = b.length();
        if (m == 0) return n;
        if (n == 0) return m;
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        for (int j = 0; j <= n; j++) prev[j] = j;
        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(
                    curr[j - 1] + 1,
                    prev[j] + 1),
                    prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[n];
    }
}
