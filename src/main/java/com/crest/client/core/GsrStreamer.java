package com.crest.client.core;

import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// Streams the screen to an RTMP ingest (YouTube/Twitch) using gpu-screen-recorder.
// Captures on the GPU only (no per-frame CPU readback) and encodes with the
// hardware encoder, like OBS does on Linux.
public class GsrStreamer {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("GsrStreamer");

    private static final AtomicBoolean streaming = new AtomicBoolean(false);
    private static long startTimeMs;
    private static Process recorderProcess;
    private static volatile int lastCaptureFps;
    private static final java.util.regex.Pattern FPS_STATS =
        java.util.regex.Pattern.compile("update fps:\\s*(\\d+)");

    public static boolean isStreaming() { return streaming.get(); }
    public static long getStartTime() { return startTimeMs; }
    // Actual capture framerate reported by gpu-screen-recorder ("update fps: N"),
    // 0 until the first stats line arrives.
    public static int getCaptureFps() { return lastCaptureFps; }

    public static void start(String url, int fps, int bitrate, boolean audioEnabled,
                             String encoder, double scaleFactor,
                             String bitrateMode, String qualityPreset, boolean micEnabled,
                             String frameRateMode) {
        if (streaming.getAndSet(true)) return;
        lastCaptureFps = 0;

        String monitor = MonitorUtil.getFocusedMonitor();
        if (monitor == null) {
            LOGGER.warn("Could not determine monitor to capture");
            streaming.set(false);
            return;
        }

        // Never request a capture frame rate above the monitor's refresh rate:
        // more frames than the display can produce just overloads the encoder
        // and shows up as "poor signal" on YouTube.
        int refresh = MonitorUtil.getFocusedMonitorRefresh();
        int effFps = (refresh > 0 && fps > refresh) ? refresh : fps;
        if (effFps != fps) {
            LOGGER.info("Capping stream fps {} → {} (monitor refresh rate)", fps, effFps);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("gpu-screen-recorder");
        cmd.add("-w"); cmd.add(monitor);
        cmd.add("-f"); cmd.add(String.valueOf(effFps));
        // Frame rate mode: cfr forces a constant 60fps even when the game runs
        // below the target, overloading the encoder (frame gaps → "poor signal").
        // auto = content mode, which follows the source and avoids that.
        String fm = "content";
        if (frameRateMode != null && !frameRateMode.isEmpty() && !frameRateMode.equals("auto")) {
            fm = frameRateMode;
        }
        cmd.add("-fm"); cmd.add(fm);

        // Map the configured encoder to a GSR codec (-k). Default to h264
        // (YouTube-safe); hevc when the user picked a HEVC encoder.
        String codec = "h264";
        if (encoder != null && encoder.startsWith("hevc")) codec = "hevc";
        cmd.add("-k"); cmd.add(codec);

        String bm = "cbr";
        if (bitrateMode != null && bitrateMode.equals("qp")) bm = "qp";
        else if (bitrateMode != null && bitrateMode.equals("vbr")) bm = "vbr";
        cmd.add("-bm"); cmd.add(bm);
        // cbr = constant bitrate (kbps); qp/vbr = constant quality preset.
        if (bm.equals("cbr")) {
            cmd.add("-q"); cmd.add(String.valueOf(bitrate));
        } else {
            String preset = (qualityPreset == null || qualityPreset.isEmpty()) ? "very_high" : qualityPreset;
            cmd.add("-q"); cmd.add(preset);
        }
        cmd.add("-c"); cmd.add("flv");

        // Output resolution: scale the monitor to fit within WxH, preserving
        // aspect ratio (GSR's -s behavior). 1.0x = native, no scaling.
        if (scaleFactor > 0 && Math.abs(scaleFactor - 1.0) > 0.001) {
            int[] res = MonitorUtil.getFocusedMonitorSize();
            if (res[0] > 0 && res[1] > 0) {
                int sw = Math.max(2, (int) Math.round(res[0] * scaleFactor));
                int sh = Math.max(2, (int) Math.round(res[1] * scaleFactor));
                cmd.add("-s"); cmd.add(sw + "x" + sh);
            }
        }

        if (audioEnabled) {
            // default_output = desktop/system audio. Append the default input
            // (microphone) when "Mic Audio" is enabled, GSR mixes both sources.
            String audioArg = micEnabled ? "default_output|default_input" : "default_output";
            cmd.add("-a"); cmd.add(audioArg);
            cmd.add("-ac"); cmd.add("aac");
        }
        cmd.add("-o"); cmd.add(url);

        LOGGER.info("Starting gpu-screen-recorder stream → {}", url);
        LOGGER.info("gpu-screen-recorder command: {}", String.join(" ", cmd));

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            recorderProcess = pb.start();
            pipeOutput(recorderProcess);
        } catch (IOException e) {
            LOGGER.warn("Failed to launch gpu-screen-recorder: {}", e.getMessage());
            streaming.set(false);
            recorderProcess = null;
            return;
        }

        startTimeMs = System.currentTimeMillis();
    }

    public static void stop() {
        streaming.set(false);
        lastCaptureFps = 0;

        if (recorderProcess != null && recorderProcess.isAlive()) {
            long pid = recorderProcess.pid();
            try {
                Runtime.getRuntime().exec(new String[]{"kill", "-INT", String.valueOf(pid)});
                if (!recorderProcess.waitFor(10, TimeUnit.SECONDS)) {
                    recorderProcess.destroyForcibly();
                    recorderProcess.waitFor(5, TimeUnit.SECONDS);
                }
            } catch (Exception ignored) {
                recorderProcess.destroyForcibly();
            }
            recorderProcess = null;
        }
    }

    // Relay gpu-screen-recorder's output to the log/console so the user can see
    // fps/bitrate like the ffmpeg stats lines did. Also acts as a watchdog: when
    // the process exits while we still think we are streaming, this was an
    // unexpected crash — reset state, surface the error and notify the user.
    private static void pipeOutput(Process p) {
        Thread t = new Thread(() -> pipeStream(p.getInputStream(), p), "gsr-out");
        Thread u = new Thread(() -> pipeStream(p.getErrorStream(), p), "gsr-err");
        t.setDaemon(true);
        u.setDaemon(true);
        t.start();
        u.start();
    }

    private static void pipeStream(java.io.InputStream in, Process p) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = r.readLine()) != null) {
                System.out.println("[gpu-screen-recorder] " + line);
                java.util.regex.Matcher m = FPS_STATS.matcher(line);
                if (m.find()) lastCaptureFps = Integer.parseInt(m.group(1));
            }
        } catch (Exception ignored) {}
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (streaming.compareAndSet(true, false)) {
            lastCaptureFps = 0;
            Streamer.setLastError("gpu-screen-recorder exited unexpectedly");
            try { NotificationToast.show("Stream dropped — gpu-screen-recorder exited", 0xFFFF5B6E); }
            catch (Throwable ignored) {}
        }
    }
}