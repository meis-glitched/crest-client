package com.crest.client.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class EncoderProbe {
    private static final String[][] ENCODERS = {
        {"h264_nvenc",     "NVIDIA NVENC H.264"},
        {"hevc_nvenc",     "NVIDIA NVENC HEVC"},
        {"h264_amf",       "AMD AMF H.264"},
        {"hevc_amf",       "AMD AMF HEVC"},
        {"h264_videotoolbox", "Apple VideoToolbox H.264"},
        {"hevc_videotoolbox", "Apple VideoToolbox HEVC"},
        {"h264_vaapi",     "Linux VAAPI H.264"},
        {"hevc_vaapi",     "Linux VAAPI HEVC"},
        {"h264_qsv",       "Intel QSV H.264"},
        {"hevc_qsv",       "Intel QSV HEVC"},
        {"libx264",        "Software x264"},
        {"libx265",        "Software x265"},
    };

    private static final String[] PRESETS_SW = {"ultrafast", "superfast", "veryfast", "faster", "fast",
        "medium", "slow", "slower", "veryslow", "placebo"};
    private static final String[] PRESETS_HW = {"fast", "medium", "slow"};

    private static volatile String bestEncoder = "libx264";
    private static volatile String[] encoderList = {"libx264"};

    public static void probe() {
        List<String> candidates = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("ffmpeg", "-hide_banner", "-encoders").start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    for (String[] enc : ENCODERS) {
                        if (line.contains(" " + enc[0] + " ") && !candidates.contains(enc[0])) {
                            candidates.add(enc[0]);
                        }
                    }
                }
            }
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {}

        // A compiled-in encoder is NOT necessarily usable (e.g. h264_nvenc is built
        // into ffmpeg but fails on machines without the matching hardware/driver).
        // Only the encoders that actually produce frames are kept, so the client
        // never picks h264_nvenc/h264_amf/h264_qsv on a GPU that can't run them.
        // Keep the ENCODERS priority order: hardware first, software last.
        List<String> validated = new ArrayList<>();
        for (String[] enc : ENCODERS) {
            if (candidates.contains(enc[0]) && encoderWorks(enc[0])) {
                validated.add(enc[0]);
            }
        }

        if (validated.isEmpty()) validated.add("libx264");

        encoderList = validated.toArray(new String[0]);
        bestEncoder = encoderList[0];
    }

    // ponytail: probe each candidate with a real 2-frame encode to null. h264_nvenc
    // is compiled into ffmpeg but errors on AMD/Intel-only boxes; filtering by
    // livable output (not just name match) is the only reliable way to build the list.
    private static boolean encoderWorks(String encoder) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-hide_banner");
        cmd.add("-loglevel"); cmd.add("error");
        cmd.add("-f"); cmd.add("lavfi");
        cmd.add("-i"); cmd.add("testsrc=duration=0.4:size=320x180:rate=10");
        cmd.add("-c:v"); cmd.add(encoder);
        if (isVaapi(encoder)) {
            if (vaapiDevice == null) return false;
            cmd.add("-vaapi_device"); cmd.add(vaapiDevice);
            cmd.add("-vf"); cmd.add("format=nv12,hwupload");
        }
        cmd.add("-frames:v"); cmd.add("2");
        cmd.add("-f"); cmd.add("null");
        cmd.add("-");
        try {
            Process p = new ProcessBuilder(cmd).start();
            if (p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0) {
                return true;
            }
            if (p.isAlive()) {
                p.destroyForcibly();
                p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static String getBestEncoder() { return bestEncoder; }
    public static String[] getAvailableEncoders() { return encoderList; }

    public static String[] getPresets(String encoder) {
        if (encoder.startsWith("libx") || encoder.startsWith("software") || encoder.equals("libx264") || encoder.equals("libx265")) {
            return PRESETS_SW;
        }
        return PRESETS_HW;
    }

    public static int defaultPresetIndex(String encoder) {
        if (encoder.startsWith("libx")) return 0; // ultrafast
        return 1; // medium for HW
    }

    public static boolean isVaapi(String encoder) {
        return encoder.endsWith("_vaapi");
    }

    // Path to a VAAPI render device, or null if unavailable.
    private static String vaapiDevice;

    public static String getVaapiDevice() { return vaapiDevice; }

    public static boolean hasVaapiDevice() { return vaapiDevice != null; }

    // --- Audio backend probing ---

    private static String audioBackend = "pulse";
    private static String[] audioDevices = {"default"};

    static {
        probeAudio();
        probeVaapi();
    }

    private static void probeVaapi() {
        // Only probe if VAAPI encoders are available
        for (String[] enc : ENCODERS) {
            if (enc[0].endsWith("_vaapi")) {
                for (String dev : new String[]{"/dev/dri/renderD128", "/dev/dri/renderD129", "/dev/dri/card0"}) {
                    if (new java.io.File(dev).exists()) { vaapiDevice = dev; return; }
                }
                break;
            }
        }
    }

    private static void probeAudio() {
        // Check for pipewire or pulse
        try {
            Process p = new ProcessBuilder("pactl", "info").start();
            if (p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0) {
                audioBackend = "pulse";
            }
        } catch (Exception ignored) {}

        try {
            Process p = new ProcessBuilder("pw-cli", "info").start();
            if (p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0) {
                audioBackend = "pipewire";
            }
        } catch (Exception ignored) {}

        // Get default sink name. We capture the sink's MONITOR source (system
        // audio), NOT the sink itself: ffmpeg's pulse input treats the device
        // name as a SOURCE, so passing a raw sink name falls back to the default
        // source (microphone). The monitor is what plays through the speakers.
        if (audioBackend.equals("pulse")) {
            try {
                Process p = new ProcessBuilder("pactl", "get-default-sink").start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line = r.readLine();
                    if (line != null && !line.isBlank()) {
                        audioDevices = new String[]{line.trim() + ".monitor", line.trim()};
                    }
                }
                p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        } else if (audioBackend.equals("pipewire")) {
            try {
                Process p = new ProcessBuilder("pw-cli", "list-objects", "Node").start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.contains("node.name")) {
                            String name = line.split("=")[1].trim().replace("\"", "");
                            audioDevices = new String[]{name, "default"};
                            break;
                        }
                    }
                }
                p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
    }

    public static String getAudioBackend() { return audioBackend; }
    public static String[] getAudioDevices() { return audioDevices; }
    
    // Returns the backend that actually works with ffmpeg
    public static String getWorkingAudioBackend() {
        // Try pipewire first, but fall back to pulse if pipewire format not supported
        if (audioBackend.equals("pipewire")) {
            // Check if ffmpeg supports the pipewire input format
            try {
                Process p = new ProcessBuilder("ffmpeg", "-hide_banner", "-formats").start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.contains("pipewire")) {
                            return "pipewire";
                        }
                    }
                }
                p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ignored) {}
            // Fall back to pulse
            return "pulse";
        }
        return audioBackend;
    }
}
