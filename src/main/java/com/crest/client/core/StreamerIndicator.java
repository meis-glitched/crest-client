package com.crest.client.core;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class StreamerIndicator extends HudModule {
    private long lastStatsReset;
    private int lastCaptured;
    private int lastEncoded;
    private int captureFps;
    private int encodeFps;

    // ponytail: cached stats string to avoid per-frame String.format + Component alloc
    private long cachedStatsElapsed = -1;
    private int cachedStatsFps = -1;
    private long cachedStatsDrop = -1;
    private boolean cachedStatsErr;
    private boolean cachedStatsAudio;
    private boolean cachedStatsGsr;
    private int cachedStatsGsrFps;
    private String cachedStats;
    private net.minecraft.network.chat.Component cachedStatsComp;

    public StreamerIndicator() {
        super(-1, 24);
        lastStatsReset = System.currentTimeMillis();
    }

    @Override
    public String getId() { return "streamer_indicator"; }
    @Override
    public String getName() { return "Streamer Indicator"; }
    @Override
    public String getDescription() { return "Shows LIVE indicator with FPS and bitrate when streaming"; }
    @Override
    public boolean isEnabled() { return true; }

    @Override
    public int getWidth() {
        return Minecraft.getInstance().font.width("LIVE 00:00 | 00fps | 00000kbps") + 14;
    }

    @Override
    public int getHeight() {
        return Minecraft.getInstance().font.lineHeight + 6;
    }

    @Override
    public void render(GuiGraphicsExtractor g, Minecraft mc, DeltaTracker d) {
        if (!Streamer.isStreaming()) return;

        long now = System.currentTimeMillis();
        boolean gsr = Streamer.isUsingGsr();
        int captured = gsr ? 0 : (int) Streamer.getCapturedFrames();
        int encoded = gsr ? 0 : (int) Streamer.getEncodedFrames();
        long dropproxy = gsr ? 0 : Streamer.getDroppedFrames();

        if (now - lastStatsReset > 1000) {
            captureFps = captured - lastCaptured;
            encodeFps = encoded - lastEncoded;
            lastCaptured = captured;
            lastEncoded = encoded;
            lastStatsReset = now;
        }

        long elapsed = (now - Streamer.getStartTime()) / 1000;
        String err = Streamer.getLastError();

        int gsrFps = GsrStreamer.getCaptureFps();
        if (gsrFps < 0) gsrFps = 0;

        // ponytail: only rebuild the stats String + Component once per second
        // (when FPS/drop counters refresh), not every frame.
        if (cachedStatsElapsed != elapsed || cachedStatsFps != encodeFps || cachedStatsDrop != dropproxy
                || cachedStatsErr != (err != null) || cachedStatsAudio != Streamer.didAudioFallBack()
                || cachedStatsGsr != gsr || cachedStatsGsrFps != gsrFps
                || cachedStats == null) {
            cachedStatsElapsed = elapsed;
            cachedStatsFps = encodeFps;
            cachedStatsDrop = dropproxy;
            cachedStatsErr = (err != null);
            cachedStatsAudio = Streamer.didAudioFallBack();
            cachedStatsGsr = gsr;
            cachedStatsGsrFps = gsrFps;

            String stats;
            if (gsr) {
                // gpu-screen-recorder: show its captured fps and the configured
                // (CBR) bitrate. No frame-drop counters in this backend.
                int fps = gsrFps > 0 ? gsrFps : Streamer.getCurrentFps();
                stats = String.format("LIVE %02d:%02d | %dfps | %dkbps",
                    elapsed / 60, elapsed % 60, fps, Streamer.getCurrentBitrate());
            } else {
                stats = String.format("LIVE %02d:%02d | %dfps | %d drop",
                    elapsed / 60, elapsed % 60, encodeFps, dropproxy);
            }
            if (err != null) stats = "ERR: " + err;
            else if (Streamer.didAudioFallBack()) stats = "LIVE (no audio) " + stats.substring(5);
            cachedStats = stats;
            cachedStatsComp = Component.literal(stats);
        }

        int textW = mc.font.width(cachedStats);
        int rx = x < 0 ? mc.getWindow().getGuiScaledWidth() - textW - 14 - 2 : x;
        int ry = y;

        HudBackground.draw(g, rx, ry, textW + 14, mc.font.lineHeight + 6);
        g.text(mc.font, cachedStatsComp, rx + 10, ry + 3, 0xFFFFFFFF);

        boolean dotOn = ((System.currentTimeMillis() / (err != null ? 300 : 600)) % 2) == 0;
        int dotSize = 6;
        int dx = rx + 3;
        int dy = ry + (mc.font.lineHeight + 6 - dotSize) / 2;
        if (dotOn) {
            g.fill(dx, dy, dx + dotSize, dy + dotSize, err != null ? 0xFFFF3333 : 0xFF33FF33);
        }
    }
}
