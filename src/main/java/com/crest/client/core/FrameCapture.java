package com.crest.client.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

public class FrameCapture {
    private static volatile boolean capturing;
    private static int targetFps;
    private static long lastCaptureNanos;

    // When true (streaming/recording), capture runs at a strict fixed interval
    // matching targetFps instead of the adaptive FrameBudget. The budget skips
    // frames when the game is busy, which desyncs the real capture rate from the
    // fps declared to ffmpeg and makes the output play back too fast/slow.
    private static volatile boolean strictRate;

    // ponytail: pooled GPU staging buffer — allocated once per (w,h) instead of
    // every frame, since per-frame createBuffer()/close() was capping capture at ~53fps
    private static GpuBuffer stagingBuffer;
    private static int stagingW = -1;
    private static int stagingH = -1;

    // ponytail: 24 buffers — increased for higher FPS stability without depleting free pool
    private static final ArrayBlockingQueue<ByteBuffer> freePool = new ArrayBlockingQueue<>(24);
    private static final ArrayBlockingQueue<ByteBuffer> filledQueue = new ArrayBlockingQueue<>(24);

    // Actual captured dimensions (taken from the live render target, which may
    // differ from the window size on HiDPI/scaled displays). ffmpeg must be told
    // these exact values or the frames come out garbled.
    private static volatile int capturedW = -1;
    private static volatile int capturedH = -1;

    public static void start(int fps, int width, int height) {
        start(fps, width, height, false);
    }

    public static void start(int fps, int width, int height, boolean strict) {
        targetFps = fps;
        strictRate = strict;
        lastCaptureNanos = 0;
        // Buffers are (re)allocated lazily from the real render-target size on the
        // first captured frame, so we don't trust the window dimensions here.
        freePool.clear();
        filledQueue.clear();
        capturing = true;
    }

    public static int getCapturedWidth() { return capturedW; }
    public static int getCapturedHeight() { return capturedH; }

    public static void stop() {
        capturing = false;
        strictRate = false;
        if (stagingBuffer != null) {
            try { stagingBuffer.close(); } catch (Exception ignored) {}
            stagingBuffer = null;
            stagingW = -1;
            stagingH = -1;
        }
    }

    // ponytail: reuse a single GPU staging buffer across frames; only reallocate
    // when the capture resolution changes. Avoids per-frame GPU alloc (53fps cap).
    private static synchronized GpuBuffer getStagingBuffer(int w, int h, int bufSize) {
        if (stagingBuffer != null && stagingW == w && stagingH == h) {
            return stagingBuffer;
        }
        if (stagingBuffer != null) {
            try { stagingBuffer.close(); } catch (Exception ignored) {}
            stagingBuffer = null;
        }
        try {
            stagingBuffer = RenderSystem.getDevice().createBuffer(
                () -> "crest-capture", 9, bufSize);
            stagingW = w;
            stagingH = h;
        } catch (Exception e) {
            stagingBuffer = null;
        }
        return stagingBuffer;
    }

    public static boolean isCapturing() { return capturing; }
    public static ArrayBlockingQueue<ByteBuffer> getFilledQueue() { return filledQueue; }
    public static ArrayBlockingQueue<ByteBuffer> getFreePool() { return freePool; }

    public static void onRenderEnd() {
        if (!capturing) return;

        long now = System.nanoTime();
        if (strictRate) {
            // Streaming/recording: capture at a fixed interval matching the fps we
            // declare to ffmpeg so playback speed is correct. Skip only if we
            // haven't reached the next capture time yet.
            long intervalNs = 1_000_000_000L / Math.max(1, targetFps);
            if (now - lastCaptureNanos < intervalNs) return;
            lastCaptureNanos = now;
        } else {
            // ponytail: ACFB — only capture when there is spare frame budget. Protects
            // gameplay FPS by skipping the capture/readback tax when the game is busy.
            int displayHz = 60;
            try {
                Minecraft mc = Minecraft.getInstance();
                int rr = mc.getWindow().getRefreshRate();
                if (rr > 0) displayHz = rr;
            } catch (Throwable ignored) {}
            if (!FrameBudget.shouldCapture(now, targetFps, displayHz)) return;
            lastCaptureNanos = now;
        }

        Minecraft mc = Minecraft.getInstance();
        RenderTarget target = mc.getMainRenderTarget();
        GpuTexture texture = target.getColorTexture();
        if (texture == null) return;

        int cw = target.width;
        int ch = target.height;
        int pixSize = texture.getFormat().pixelSize();
        int bufSize = cw * ch * pixSize;

        // Size our buffers from the REAL render target. On HiDPI/scaled displays
        // this differs from the window size; using the window size here is what
        // produced garbled/glitching frames at 1.0x and 0.75x scale.
        if (capturedW != cw || capturedH != ch) {
            capturedW = cw;
            capturedH = ch;
            freePool.clear();
            filledQueue.clear();
            if (stagingBuffer != null) {
                try { stagingBuffer.close(); } catch (Exception ignored) {}
                stagingBuffer = null;
                stagingW = -1;
                stagingH = -1;
            }
            for (int i = 0; i < 16; i++) {
                freePool.offer(ByteBuffer.allocateDirect(bufSize));
            }
        }

        ByteBuffer dst = freePool.poll();
        if (dst == null) {
            Streamer.addDropped();
            return;
        }
        dst.clear();

        try {
            GpuBuffer staging = getStagingBuffer(cw, ch, bufSize);
            if (staging == null) {
                freePool.offer(dst);
                return;
            }

            CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();

            enc.copyTextureToBuffer(texture, staging, 0L, () -> {
                try (var view = enc.mapBuffer(staging, true, false)) {
                    ByteBuffer src = view.data().slice(0, Math.min((int)staging.size(), dst.capacity()));
                    dst.put(src);
                    dst.flip();
                    Streamer.addCaptured();
                    if (ReplayBuffer.isActive()) {
                        ReplayBuffer.addFrame(dst, cw, ch);
                    }
                    if (!filledQueue.offer(dst)) {
                        Streamer.addDropped();
                        freePool.offer(dst);
                    }
                } catch (Exception e) {
                    freePool.offer(dst);
                }
            }, 0);
        } catch (Exception e) {
            freePool.offer(dst);
        }
    }
}
