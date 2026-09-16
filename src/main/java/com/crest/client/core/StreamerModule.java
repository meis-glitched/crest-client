package com.crest.client.core;

import com.crest.client.core.event.TickEvent;
import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;
import com.crest.client.core.setting.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class StreamerModule implements CrestModule {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("Streamer");
    private final StringSetting rtmpUrl = new StringSetting("RTMP URL",
        "Twitch: rtmp://live.twitch.tv/app/STREAM_KEY YouTube: rtmp://a.rtmp.youtube.com/live2/STREAM_KEY",
        "rtmp://live.twitch.tv/app/");
    private final IntegerSetting bitrate = new IntegerSetting("Bitrate (kbps)", 500, 50000, 6000);
    private final IntegerSetting fps = new IntegerSetting("FPS", 15, 120, 60);
    private final ModeSetting frameRateMode = new ModeSetting("Frame Rate Mode",
        "auto = content mode (follows the game, avoids encoder overload when it runs below target FPS). cfr = strict constant FPS (can drop frames → 'poor signal').",
        new String[]{"auto", "cfr", "vfr", "content"}, 0);
    private final ModeSetting scale = new ModeSetting("Resolution Scale",
        new String[]{"0.25x", "0.5x", "0.75x", "1.0x"}, 3);
    private final ModeSetting encoder = new ModeSetting("Encoder",
        "Probed at startup; shows available encoders", new String[]{"libx264"}, 0);
    private final ModeSetting encoderPreset = new ModeSetting("Encoder Preset",
        new String[]{"fast", "medium", "slow"}, 0);
    private final BooleanSetting audioEnabled = new BooleanSetting("Stream Audio", true);
    private final StringSetting audioDevice = new StringSetting("Audio Device",
        "PulseAudio sink name or 'default'. Leave empty for default.", "default");
    private final ModeSetting bitrateMode = new ModeSetting("Bitrate Mode",
        "cbr = constant bitrate (uses Bitrate kbps). qp/vbr = constant quality via Quality Preset.",
        new String[]{"cbr", "qp", "vbr"}, 0);
    private final ModeSetting qualityPreset = new ModeSetting("Quality Preset",
        "Used when Bitrate Mode is qp or vbr (constant quality, no kbps target).",
        new String[]{"medium", "high", "very_high", "ultra"}, 2);
    private final BooleanSetting micEnabled = new BooleanSetting("Mic Audio",
        "Add the microphone (default input) alongside desktop audio.", false);
    private final BooleanSetting recordWhileStreaming = new BooleanSetting("Record While Streaming", false);
    private final ModeSetting backend = new ModeSetting("Stream Backend",
        "gpu-screen-recorder = GPU capture + HW encode (OBS-style). ffmpeg = in-process fallback.",
        new String[]{"gpu-screen-recorder", "ffmpeg"}, 0);
    private final KeybindSetting streamKey = new KeybindSetting("Stream Toggle", GLFW.GLFW_KEY_F8);

    @Override
    public String getId() { return "streamer"; }
    @Override
    public String getName() { return "Live Streamer"; }
    @Override
    public String getDescription() { return "Stream gameplay to Twitch/YouTube via RTMP"; }
    @Override
    public String getCategory() { return "Misc"; }
    @Override
    public boolean isEnabled() { return true; }

    @Override
    public boolean selfHandlesKeybinds() { return true; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(rtmpUrl, bitrate, fps, frameRateMode, scale, encoder, encoderPreset, audioEnabled, audioDevice,
            bitrateMode, qualityPreset, micEnabled, recordWhileStreaming, backend, streamKey);
    }

    @Override
    public void onInitialize() {
        EncoderProbe.probe();
        String[] available = EncoderProbe.getAvailableEncoders();

        java.lang.reflect.Field modesField;
        try {
            modesField = ModeSetting.class.getDeclaredField("modes");
            modesField.setAccessible(true);
            modesField.set(encoder, available);
        } catch (Exception ignored) {}
        encoder.set(0); // best encoder is first

        String[] presets = EncoderProbe.getPresets(available[0]);
        try {
            modesField = ModeSetting.class.getDeclaredField("modes");
            modesField.setAccessible(true);
            modesField.set(encoderPreset, presets);
        } catch (Exception ignored) {}
        encoderPreset.set(EncoderProbe.defaultPresetIndex(available[0]));

        CrestModules.getEventBus().subscribe(TickEvent.class, this::onTick);
    }

    private void onTick(TickEvent event) {
        if (streamKey.wasPressed()) {
            toggleStreaming();
        }
    }

    public String getStreamUrl() { return rtmpUrl.get(); }
    public void setStreamUrl(String url) { rtmpUrl.set(url); }
    public int getBitrate() { return bitrate.get(); }
    public void setBitrate(int value) { bitrate.set(value); }
    public int getFps() { return fps.get(); }
    public void setFps(int value) { fps.set(value); }
    public String getFrameRateMode() { return frameRateMode.getMode(); }
    public int getFrameRateModeIndex() { return frameRateMode.get(); }
    public void setFrameRateModeIndex(int index) { frameRateMode.set(index); }
    public double getScaleFactor() {
        String s = scale.getMode();
        return Double.parseDouble(s.substring(0, s.length() - 1));
    }
    public void setScale(int index) { scale.set(index); }
    public String getEncoder() { return encoder.getMode(); }
    public void setEncoder(String mode) { encoder.setMode(mode); }
    public String getEncoderPreset() { return encoderPreset.getMode(); }
    public void setEncoderPreset(String mode) { encoderPreset.setMode(mode); }
    public boolean isAudioEnabled() { return audioEnabled.get(); }
    public void setAudioEnabled(boolean value) { audioEnabled.set(value); }
    public String getAudioDevice() { return audioDevice.get(); }
    public void setAudioDevice(String device) { audioDevice.set(device); }
    public String getBitrateMode() { return bitrateMode.getMode(); }
    public int getBitrateModeIndex() { return bitrateMode.get(); }
    public void setBitrateModeIndex(int index) { bitrateMode.set(index); }
    public String getQualityPreset() { return qualityPreset.getMode(); }
    public int getQualityPresetIndex() { return qualityPreset.get(); }
    public void setQualityPresetIndex(int index) { qualityPreset.set(index); }
    public boolean isMicEnabled() { return micEnabled.get(); }
    public void setMicEnabled(boolean value) { micEnabled.set(value); }
    public boolean isRecordWhileStreaming() { return recordWhileStreaming.get(); }
    public void setRecordWhileStreaming(boolean value) { recordWhileStreaming.set(value); }
    public boolean isGsrBackend() { return backend.get() == 0; }

    private String resolveAudioDevice() {
        String configured = audioDevice.get();
        if (configured == null || configured.isBlank() || configured.equals("default")) {
            String[] detected = EncoderProbe.getAudioDevices();
            if (detected != null && detected.length > 0) return detected[0];
        }
        return configured;
    }

    public void toggleStreaming() {
        if (Streamer.isStreaming()) {
            Streamer.stop();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w <= 0 || h <= 0) {
            LOGGER.warn("Toggle aborted: window too small ({}×{})", w, h);
            return;
        }
        String url = rtmpUrl.get();
        if (url == null || url.isEmpty() || url.equals("rtmp://live.twitch.tv/app/")) {
            LOGGER.warn("Toggle aborted: no RTMP URL set — paste the full URL in Settings or use /streamurl");
            CrestModules.notify("Set an RTMP URL first (Settings or /streamurl)", 0xFFFFB74D);
            return;
        }
        if (!Streamer.isUrlAllowed(url)) {
            LOGGER.warn("Toggle aborted: invalid RTMP URL '{}'", url);
            CrestModules.notify("Invalid RTMP URL", 0xFFFF5B6E);
            return;
        }
        if (Streamer.isMissingStreamKey(url)) {
            LOGGER.warn("Toggle aborted: RTMP URL missing stream key: '{}'", url);
            CrestModules.notify("RTMP URL missing stream key — add it from YouTube/Twitch", 0xFFFF5B6E);
            return;
        }
        LOGGER.info("Starting stream → {}", url);
        Streamer.start(url, fps.get(), w, h, bitrate.get(),
            encoder.getMode(), encoderPreset.getMode(),
            audioEnabled.get() ? resolveAudioDevice() : "none",
            getScaleFactor(), null, backend.get() == 0,
            bitrateMode.getMode(), qualityPreset.getMode(), micEnabled.get(),
            frameRateMode.getMode());
    }

    @Override
    public void onEnable() {}
    @Override
    public void onDisable() {
        if (Streamer.isStreaming()) Streamer.stop();
    }
}
