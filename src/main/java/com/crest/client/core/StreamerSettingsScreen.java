package com.crest.client.core;

import com.crest.client.ui.ColorUtil;
import com.crest.client.ui.Panel;
import com.crest.client.ui.Theme;
import com.crest.client.ui.ToggleSwitch;
import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class StreamerSettingsScreen extends Screen implements TextCapturingScreen {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("StreamerSettings");
    private final Screen parent;
    private final StreamerModule module;
    private String urlText;
    private boolean urlFocused = true;
    private int cursorBlink;
    private int bitrate;
    private int streamFps;
    private int frameRateModeIdx;
    private int encoderIdx;
    private int presetIdx;
    private int scaleIdx;
    private boolean audioEnabled;
    private boolean recordWhileStreaming;
    private int bitrateModeIdx;
    private int qualityPresetIdx;
    private boolean micEnabled;
    private String audioDeviceText;
    private boolean audioFocused;
    private boolean streaming;
    private int panelW = 420;
    private int panelH = 560;
    private int panelX, panelY;

    private boolean draggingBitrate;
    private static final int SLIDER_W = 200;
    private static final int SLIDER_H = 6;
    private static final String[] SCALES = {"0.25x", "0.5x", "0.75x", "1.0x"};
    private static final String[] BITRATE_MODES = {"cbr", "qp", "vbr"};
    private static final String[] QUALITY_PRESETS = {"medium", "high", "very_high", "ultra"};
    private static final String[] FRAME_RATE_MODES = {"auto", "cfr", "vfr", "content"};

    public StreamerSettingsScreen(Screen parent) {
        super(Component.literal("Streamer Settings"));
        this.parent = parent;
        this.module = (StreamerModule) CrestModules.get("streamer");
        this.urlText = module.getStreamUrl();
        this.bitrate = module.getBitrate();
        this.streamFps = module.getFps();
        this.frameRateModeIdx = module.getFrameRateModeIndex();
        String currentScale = module.getScaleFactor() + "x";
        String[] scales = {"0.25x", "0.5x", "0.75x", "1.0x"};
        this.scaleIdx = 3;
        for (int i = 0; i < SCALES.length; i++) { if (SCALES[i].equals(currentScale)) { scaleIdx = i; break; } }
        this.audioEnabled = module.isAudioEnabled();
        this.recordWhileStreaming = module.isRecordWhileStreaming();
        this.bitrateModeIdx = module.getBitrateModeIndex();
        this.qualityPresetIdx = module.getQualityPresetIndex();
        this.micEnabled = module.isMicEnabled();
        String cfg = module.getAudioDevice();
        this.audioDeviceText = (cfg == null || cfg.isBlank() || cfg.equals("default")) ? "" : cfg;
        this.streaming = Streamer.isStreaming();

        String[] encoders = EncoderProbe.getAvailableEncoders();
        String currentEnc = module.getEncoder();
        this.encoderIdx = 0;
        for (int i = 0; i < encoders.length; i++) {
            if (encoders[i].equals(currentEnc)) { encoderIdx = i; break; }
        }
        String[] presets = EncoderProbe.getPresets(encoders[encoderIdx]);
        String currentPreset = module.getEncoderPreset();
        this.presetIdx = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i].equals(currentPreset)) { presetIdx = i; break; }
        }
    }

    @Override
    protected void init() {
        panelW = Math.min(400, width - 40);
        panelH = Math.min(560, height - 40);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Theme.tick(delta);
        int cx = panelX + panelW / 2;
        int left = panelX + 16;
        int right = panelX + panelW - 16;
        int cw = right - left;

        g.fill(0, 0, width, height, Theme.OVERLAY);
        Panel.drawGlass(g, panelX, panelY, panelW, panelH, ColorUtil.withAlpha(Theme.BG_PANEL, 240), Theme.getAnimatedAccent());

        g.centeredText(font, Component.literal("\u2699 Streamer Settings"), cx, panelY + 14, Theme.TEXT);

        String back = "< Back";
        int backW = font.width(back) + 12;
        boolean backHover = mx >= left && mx <= left + backW && my >= panelY + 6 && my <= panelY + 22;
        Panel.draw(g, left, panelY + 6, backW, 16, ColorUtil.withAlpha(backHover ? Theme.BG_HOVER : 0, 200));
        g.text(font, Component.literal(back), left + 6, panelY + 8, Theme.TEXT_DIM);

        int sy = panelY + 36;

        // RTMP URL
        g.text(font, Component.literal("RTMP URL"), left, sy, Theme.TEXT_DIM);
        int urlY = sy + 12;
        renderTextInput(g, left, urlY, cw, 20, urlText, urlFocused, "rtmp://...", mx, my);
        sy = urlY + 20 + 10;

        // FPS
        renderSlider(g, left, sy, cw, "FPS", streamFps, 15, 120, "fps", mx, my);
        sy += 36;

        // Frame Rate Mode
        g.text(font, Component.literal("Frame Rate Mode"), left, sy, Theme.TEXT_DIM);
        String fmLabel = FRAME_RATE_MODES[Math.min(frameRateModeIdx, FRAME_RATE_MODES.length - 1)];
        boolean fmHover = mx >= left && mx <= right && my >= sy + 10 && my <= sy + 28;
        Panel.draw(g, left, sy + 10, cw, 18, ColorUtil.withAlpha(fmHover ? Theme.BG_HOVER : Theme.BG_SURFACE, 220));
        g.text(font, Component.literal("\u25C0 " + fmLabel + " \u25B6"), cx, sy + 12, Theme.TEXT);
        sy += 34;

        // Video Bitrate
        renderSlider(g, left, sy, cw, "Video Bitrate", bitrate, 500, 50000, "kbps", mx, my);
        sy += 36;

        // Bitrate Mode
        g.text(font, Component.literal("Bitrate Mode"), left, sy, Theme.TEXT_DIM);
        String bmLabel = BITRATE_MODES[Math.min(bitrateModeIdx, BITRATE_MODES.length - 1)];
        boolean bmHover = mx >= left && mx <= right && my >= sy + 10 && my <= sy + 28;
        Panel.draw(g, left, sy + 10, cw, 18, ColorUtil.withAlpha(bmHover ? Theme.BG_HOVER : Theme.BG_SURFACE, 220));
        g.text(font, Component.literal("\u25C0 " + bmLabel + " \u25B6"), cx, sy + 12, Theme.TEXT);
        sy += 34;

        // Resolution Scale
        g.text(font, Component.literal("Resolution Scale"), left, sy, Theme.TEXT_DIM);
        String scaleLabel = SCALES[Math.min(scaleIdx, SCALES.length - 1)];
        boolean scaleHover = mx >= left && mx <= right && my >= sy + 10 && my <= sy + 28;
        Panel.draw(g, left, sy + 10, cw, 18, ColorUtil.withAlpha(scaleHover ? Theme.BG_HOVER : Theme.BG_SURFACE, 220));
        g.text(font, Component.literal("\u25C0 " + scaleLabel + " \u25B6"), cx, sy + 12, Theme.TEXT);
        sy += 34;

        // Encoder
        g.text(font, Component.literal("Encoder"), left, sy, Theme.TEXT_DIM);
        String[] encoders = EncoderProbe.getAvailableEncoders();
        String encLabel = encoderIdx < encoders.length ? encoders[encoderIdx] : "libx264";
        boolean encHover = mx >= left && mx <= right && my >= sy + 10 && my <= sy + 28;
        Panel.draw(g, left, sy + 10, cw, 18, ColorUtil.withAlpha(encHover ? Theme.BG_HOVER : Theme.BG_SURFACE, 220));
        g.text(font, Component.literal("\u25C0 " + encLabel + " \u25B6"), cx, sy + 12, Theme.TEXT);
        sy += 34;

        // Encoder Preset
        g.text(font, Component.literal("Preset"), left, sy, Theme.TEXT_DIM);
        String[] presets = EncoderProbe.getPresets(encoders[Math.min(encoderIdx, encoders.length - 1)]);
        String presetLabel = presetIdx < presets.length ? presets[presetIdx] : "medium";
        boolean presetHover = mx >= left && mx <= right && my >= sy + 10 && my <= sy + 28;
        Panel.draw(g, left, sy + 10, cw, 18, ColorUtil.withAlpha(presetHover ? Theme.BG_HOVER : Theme.BG_SURFACE, 220));
        g.text(font, Component.literal("\u25C0 " + presetLabel + " \u25B6"), cx, sy + 12, Theme.TEXT);
        sy += 34;

        // Quality Preset (used in qp/vbr bitrate modes)
        g.text(font, Component.literal("Quality Preset"), left, sy, Theme.TEXT_DIM);
        String qpLabel = QUALITY_PRESETS[Math.min(qualityPresetIdx, QUALITY_PRESETS.length - 1)];
        boolean qpHover = mx >= left && mx <= right && my >= sy + 10 && my <= sy + 28;
        Panel.draw(g, left, sy + 10, cw, 18, ColorUtil.withAlpha(qpHover ? Theme.BG_HOVER : Theme.BG_SURFACE, 220));
        g.text(font, Component.literal("\u25C0 " + qpLabel + " \u25B6"), cx, sy + 12, Theme.TEXT);
        sy += 34;

        // Audio toggle
        g.text(font, Component.literal("Stream Audio"), left, sy, Theme.TEXT_DIM);
        ToggleSwitch.render(g, right - ToggleSwitch.W, sy - 2, audioEnabled, audioEnabled ? 1f : 0f);
        sy += audioEnabled ? 24 : 20;

        // Audio device input
        if (audioEnabled) {
            g.text(font, Component.literal("Audio Device"), left, sy, Theme.TEXT_DIM);
            int adY = sy + 12;
            renderTextInput(g, left, adY, cw, 20, audioDeviceText, audioFocused, "detected device", mx, my);
            sy = adY + 20 + 10;

            // Mic Audio toggle
            g.text(font, Component.literal("Mic Audio"), left, sy, Theme.TEXT_DIM);
            ToggleSwitch.render(g, right - ToggleSwitch.W, sy - 2, micEnabled, micEnabled ? 1f : 0f);
            sy += 24;
        }

        // Record while streaming
        g.text(font, Component.literal("Record While Streaming"), left, sy, Theme.TEXT_DIM);
        ToggleSwitch.render(g, right - ToggleSwitch.W, sy - 2, recordWhileStreaming, recordWhileStreaming ? 1f : 0f);
        sy += 22;

        sy += 8;

        // Start/stop button
        boolean streamingActive = Streamer.isStreaming();
        String streamLabel = streamingActive ? "\u25A0 Stop Streaming" : "\u25B6 Start Streaming";
        int btnW = Math.min(cw, 220);
        int btnX = cx - btnW / 2;
        int btnH = 32;
        boolean btnHover = mx >= btnX && mx <= btnX + btnW && my >= sy && my <= sy + btnH;
        int btnColor = streamingActive ? 0xFFFF4444 : Theme.getAnimatedAccent();
        Panel.draw(g, btnX, sy, btnW, btnH, ColorUtil.withAlpha(btnHover ? ColorUtil.lerpARGB(btnColor, 0xFFFFFFFF, 0.15f) : btnColor, 220));
        g.centeredText(font, Component.literal(streamLabel), cx, sy + (btnH - 8) / 2, 0xFFFFFFFF);

        sy += btnH + 6;
        g.centeredText(font, Component.literal("F8 to toggle \u00B7 Stream state saves automatically"), cx, sy, Theme.TEXT_FAINT);
    }

    private void renderTextInput(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, boolean focused, String placeholder, int mx, int my) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        Panel.draw(g, x, y, w, h, ColorUtil.withAlpha(focused || hover ? Theme.BG_HOVER : Theme.BG_SURFACE, 220));
        String display = text.isEmpty() ? placeholder : text;
        int textColor = text.isEmpty() ? Theme.TEXT_FAINT : Theme.TEXT;
        g.text(font, Component.literal(display), x + 4, y + 5, textColor);
        if (focused && (cursorBlink / 10) % 2 == 0) {
            int tx = x + 4 + font.width(text);
            g.fill(tx, y + 3, tx + 1, y + h - 3, Theme.getAnimatedAccent());
        }
    }

    private void renderSlider(GuiGraphicsExtractor g, int x, int y, int cw, String label, int val, int min, int max, String unit, int mx, int my) {
        g.text(font, Component.literal(label), x, y, Theme.TEXT_DIM);
        String valStr = val + " " + unit;
        g.text(font, Component.literal(valStr), x + cw - font.width(valStr), y, Theme.TEXT);
        float frac = (max > min) ? (float)(val - min) / (max - min) : 0f;
        frac = Math.min(Math.max(frac, 0), 1);
        int barY = y + 12;
        g.fill(x, barY, x + cw, barY + SLIDER_H, ColorUtil.withAlpha(Theme.BG_BASE, 220));
        int fillW = (int)(frac * cw);
        g.fill(x, barY, x + fillW, barY + SLIDER_H, Theme.getAnimatedAccent());
        g.fill(x + fillW - 2, barY - 2, x + fillW + 2, barY + SLIDER_H + 2, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int btn = event.buttonInfo().input();
        int left = panelX + 16;
        int right = panelX + panelW - 16;
        int cw = right - left;

        // back
        String back = "< Back";
        int backW = font.width(back) + 12;
        if (mx >= left && mx <= left + backW && my >= panelY + 6 && my <= panelY + 22) {
            saveAndClose();
            return true;
        }

        int sy = panelY + 36;

        // URL field
        int urlY = sy + 12;
        urlFocused = mx >= left && mx <= right && my >= urlY && my <= urlY + 20;
        if (urlFocused) { cursorBlink = 0; audioFocused = false; }
        sy = urlY + 20 + 10;

        // FPS slider
        draggingBitrate = false;
        if (btn == 0 && my >= sy + 12 && my <= sy + 12 + SLIDER_H + 8) {
            updateFps(mx, left, cw);
            return true;
        }
        sy += 36;

        // Frame Rate Mode selector
        if (btn == 0 && my >= sy + 10 && my <= sy + 28 && mx >= left && mx <= right) {
            if (mx < left + cw / 2) {
                frameRateModeIdx = (frameRateModeIdx - 1 + FRAME_RATE_MODES.length) % FRAME_RATE_MODES.length;
            } else {
                frameRateModeIdx = (frameRateModeIdx + 1) % FRAME_RATE_MODES.length;
            }
        }
        sy += 34;

        // Bitrate slider
        if (btn == 0 && my >= sy + 12 && my <= sy + 12 + SLIDER_H + 8) {
            draggingBitrate = true;
            updateBitrate(mx, left, cw);
            return true;
        }
        sy += 36;

        // Bitrate Mode selector
        if (btn == 0 && my >= sy + 10 && my <= sy + 28 && mx >= left && mx <= right) {
            if (mx < left + cw / 2) {
                bitrateModeIdx = (bitrateModeIdx - 1 + BITRATE_MODES.length) % BITRATE_MODES.length;
            } else {
                bitrateModeIdx = (bitrateModeIdx + 1) % BITRATE_MODES.length;
            }
        }
        sy += 34;

        // Resolution Scale selector
        if (btn == 0 && my >= sy + 10 && my <= sy + 28 && mx >= left && mx <= right) {
            if (mx < left + cw / 2) {
                scaleIdx = (scaleIdx - 1 + SCALES.length) % SCALES.length;
            } else {
                scaleIdx = (scaleIdx + 1) % SCALES.length;
            }
        }
        sy += 34;

        // Encoder selector
        if (btn == 0 && my >= sy + 10 && my <= sy + 28 && mx >= left && mx <= right) {
            String[] encoders = EncoderProbe.getAvailableEncoders();
            if (mx < left + cw / 2) {
                encoderIdx = (encoderIdx - 1 + encoders.length) % encoders.length;
            } else {
                encoderIdx = (encoderIdx + 1) % encoders.length;
            }
            String[] presets = EncoderProbe.getPresets(encoders[encoderIdx]);
            presetIdx = Math.min(presetIdx, presets.length - 1);
        }
        sy += 34;

        // Preset selector
        if (btn == 0 && my >= sy + 10 && my <= sy + 28 && mx >= left && mx <= right) {
            String[] presets = EncoderProbe.getPresets(EncoderProbe.getAvailableEncoders()[Math.min(encoderIdx, EncoderProbe.getAvailableEncoders().length - 1)]);
            if (mx < left + cw / 2) {
                presetIdx = (presetIdx - 1 + presets.length) % presets.length;
            } else {
                presetIdx = (presetIdx + 1) % presets.length;
            }
        }
        sy += 34;

        // Quality Preset selector
        if (btn == 0 && my >= sy + 10 && my <= sy + 28 && mx >= left && mx <= right) {
            if (mx < left + cw / 2) {
                qualityPresetIdx = (qualityPresetIdx - 1 + QUALITY_PRESETS.length) % QUALITY_PRESETS.length;
            } else {
                qualityPresetIdx = (qualityPresetIdx + 1) % QUALITY_PRESETS.length;
            }
        }
        sy += 34;

        // Audio toggle
        if (btn == 0 && mx >= right - ToggleSwitch.W && mx <= right && my >= sy - 2 && my <= sy + 16) {
            audioEnabled = !audioEnabled;
            return true;
        }
        sy += audioEnabled ? 24 : 20;

        // Audio device field
        if (audioEnabled) {
            int adY = sy + 12;
            audioFocused = mx >= left && mx <= right && my >= adY && my <= adY + 20;
            if (audioFocused) { cursorBlink = 0; urlFocused = false; }
            sy = adY + 20 + 10;

            // Mic Audio toggle
            if (btn == 0 && mx >= right - ToggleSwitch.W && mx <= right && my >= sy - 2 && my <= sy + 16) {
                micEnabled = !micEnabled;
                return true;
            }
            sy += 24;
        }

        // Record while streaming toggle
        if (btn == 0 && mx >= right - ToggleSwitch.W && mx <= right && my >= sy - 2 && my <= sy + 16) {
            recordWhileStreaming = !recordWhileStreaming;
            return true;
        }
        sy += 22;

        sy += 8;

        // Start/stop button
        int btnW = Math.min(cw, 220);
        int btnX = (panelX + panelW / 2) - btnW / 2;
        int btnH = 32;
        if (btn == 0 && mx >= btnX && mx <= btnX + btnW && my >= sy && my <= sy + btnH) {
            toggleStreaming();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double mx = event.x();
        int left = panelX + 16;
        int cw = panelX + panelW - 16 - left;
        if (draggingBitrate) { updateBitrate(mx, left, cw); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingBitrate = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int cp = event.codepoint();
        if (cp < 32 || cp > 126) return super.charTyped(event);

        if (urlFocused) {
            urlText += event.codepointAsString();
            module.setStreamUrl(urlText);
            return true;
        }
        if (audioFocused) {
            audioDeviceText += event.codepointAsString();
            module.setAudioDevice(audioDeviceText);
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 256) { saveAndClose(); return true; }

        // Paste (Ctrl+V / Cmd+V, resolved via isPaste() so platform quirks and the
        // correct modifier bit are handled). Must use KeyboardHandler's cached
        // clipboard — glfwGetClipboardString hangs/returns empty on Wayland, and a
        // stray 'v' char event would otherwise corrupt the field.
        if (event.isPaste() && (urlFocused || audioFocused)) {
            String clip = minecraft.keyboardHandler.getClipboard();
            if (clip != null) {
                clip = clip.replace("\r", "").replace("\n", "").replace("\t", "").trim();
                if (!clip.isEmpty()) {
                    if (urlFocused) {
                        urlText += clip;
                        module.setStreamUrl(urlText);
                    } else if (audioFocused) {
                        audioDeviceText += clip;
                        module.setAudioDevice(audioDeviceText);
                    }
                }
            }
            return true;
        }

        if (urlFocused) {
            if (key == 259 && !urlText.isEmpty()) {
                urlText = urlText.substring(0, urlText.length() - 1);
                module.setStreamUrl(urlText);
                return true;
            }
            if (key == 257 || key == 335) { urlFocused = false; return true; }
        }
if (audioFocused) {
    if (key == 259 && !audioDeviceText.isEmpty()) {
        audioDeviceText = audioDeviceText.substring(0, audioDeviceText.length() - 1);
        module.setAudioDevice(audioDeviceText);
        return true;
    }
    if (key == 257 || key == 335) { audioFocused = false; return true; }
}
        return super.keyPressed(event);
    }

    private void saveAndClose() {
        // Save scale
        module.setScale(scaleIdx);
        // Save audio device
        module.setAudioDevice(audioDeviceText);
        module.setAudioEnabled(audioEnabled);
        module.setRecordWhileStreaming(recordWhileStreaming);
        // Save bitrate mode / quality preset / mic
        module.setBitrateModeIndex(Math.min(bitrateModeIdx, BITRATE_MODES.length - 1));
        module.setQualityPresetIndex(Math.min(qualityPresetIdx, QUALITY_PRESETS.length - 1));
        module.setMicEnabled(micEnabled);
        module.setFrameRateModeIndex(Math.min(frameRateModeIdx, FRAME_RATE_MODES.length - 1));
        // Save encoder choice
        String[] encoders = EncoderProbe.getAvailableEncoders();
        if (encoderIdx >= 0 && encoderIdx < encoders.length) {
            module.setEncoder(encoders[encoderIdx]);
        }
        // Save preset
        String[] presets = EncoderProbe.getPresets(encoders[Math.min(encoderIdx, encoders.length - 1)]);
        if (presetIdx >= 0 && presetIdx < presets.length) {
            module.setEncoderPreset(presets[presetIdx]);
        }
        minecraft.setScreen(parent);
    }

    private void updateBitrate(double mx, int left, int cw) {
        float frac = (float)((mx - left) / cw);
        frac = Math.min(Math.max(frac, 0), 1);
        bitrate = Math.round(500 + frac * 49500);
        module.setBitrate(bitrate);
    }

    private void updateFps(double mx, int left, int cw) {
        float frac = (float)((mx - left) / cw);
        frac = Math.min(Math.max(frac, 0), 1);
        streamFps = Math.round(15 + frac * 105);
        module.setFps(streamFps);
    }

    private void toggleStreaming() {
        if (Streamer.isStreaming()) {
            Streamer.stop();
            streaming = false;
            NotificationToast.show("Stream stopped");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w <= 0 || h <= 0) {
            LOGGER.warn("Toggle aborted: window too small ({}×{})", w, h);
            return;
        }
        if (urlText == null || urlText.isEmpty() || urlText.equals("rtmp://live.twitch.tv/app/")) {
            LOGGER.warn("Toggle aborted: no RTMP URL set in settings");
            NotificationToast.show("Paste your RTMP URL first", 0xFFFFB74D);
            return;
        }
        if (!Streamer.isUrlAllowed(urlText)) {
            LOGGER.warn("Toggle aborted: invalid RTMP URL '{}'", urlText);
            NotificationToast.show("Invalid RTMP URL", 0xFFFF5B6E);
            return;
        }
        if (Streamer.isMissingStreamKey(urlText)) {
            LOGGER.warn("Toggle aborted: RTMP URL missing stream key: '{}'", urlText);
            NotificationToast.show("RTMP URL missing stream key — add it from YouTube/Twitch", 0xFFFF5B6E);
            return;
        }

        String[] encoders = EncoderProbe.getAvailableEncoders();
        String enc = encoders[Math.min(encoderIdx, encoders.length - 1)];
        String[] presets = EncoderProbe.getPresets(enc);
        String preset = presets[Math.min(presetIdx, presets.length - 1)];

        double sf = Double.parseDouble(SCALES[Math.min(scaleIdx, SCALES.length - 1)].replace("x", ""));
        String recPath = null;
        if (recordWhileStreaming) {
            String dir = System.getProperty("user.home") + "/Videos/";
            try { new java.io.File(dir).mkdirs(); } catch (Exception ignored) {}
            recPath = dir + "crest-stream-" + java.time.Instant.now().toString()
                .replace(":", "-").substring(0, 19) + ".mkv";
        }
        LOGGER.info("Starting stream (settings screen) → {}", urlText);
        boolean useGsr = module.isGsrBackend();
        if (useGsr) {
            // recordWhileStreaming is handled by GSR's own replay buffer; pass no file.
            recPath = null;
        }
        Streamer.start(urlText, streamFps, w, h, bitrate, enc, preset,
            audioEnabled ? audioDeviceText : "none", sf, recPath, useGsr,
            module.getBitrateMode(), module.getQualityPreset(), micEnabled,
            module.getFrameRateMode());
        streaming = true;
        NotificationToast.show("Stream started (" + enc + " @ " + streamFps + "fps)");
    }

    @Override
    public void onClose() { saveAndClose(); }
    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean isCapturingText() { return urlFocused || audioFocused; }
}
