package com.crest.client.core;

import com.crest.client.core.event.TickEvent;
import com.crest.client.core.setting.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.List;

public class ReplayBufferModule implements CrestModule {
    private final BooleanSetting enabled = new BooleanSetting("Enabled", false);
    private final IntegerSetting duration = new IntegerSetting("Duration (sec)", 5, 60, 15);
    private final KeybindSetting saveKey = new KeybindSetting("Save Clip", GLFW.GLFW_KEY_F9);

    @Override
    public String getId() { return "replay_buffer"; }
    @Override
    public String getName() { return "Replay Buffer"; }
    @Override
    public String getDescription() { return "Save the last N seconds as a video clip (F9)"; }
    @Override
    public String getCategory() { return "Misc"; }
    @Override
    public boolean isEnabled() { return enabled.get(); }

    @Override
    public boolean selfHandlesKeybinds() { return true; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(enabled, duration, saveKey);
    }

    @Override
    public void onInitialize() {
        CrestModules.getEventBus().subscribe(TickEvent.class, this::onTick);
    }

    private boolean usesGsr() {
        CrestModule m = CrestModules.get("streamer");
        return m instanceof StreamerModule && ((StreamerModule) m).isGsrBackend();
    }

    private void onTick(TickEvent event) {
        if (!enabled.get()) return;
        if (!saveKey.wasPressed()) return;

        if (usesGsr()) {
            if (GsrReplayBuffer.isSaving()) {
                NotificationToast.show("Replay buffer still saving...");
                return;
            }
            if (!GsrReplayBuffer.isActive() || GsrReplayBuffer.getDurationSec() < 2) {
                NotificationToast.show("Not enough buffered footage");
                return;
            }
            NotificationToast.show("Saving clip...");
            GsrReplayBuffer.saveAsync();
            return;
        }

        if (ReplayBuffer.isSaving()) {
            NotificationToast.show("Replay buffer still saving...");
            return;
        }
        if (!ReplayBuffer.isActive() || ReplayBuffer.getDurationSec() < 2) {
            NotificationToast.show("Not enough buffered footage");
            return;
        }
        String dir = System.getProperty("user.home") + "/Videos/";
        new File(dir).mkdirs();
        String path = dir + "crest-clip-" + java.time.Instant.now().toString()
            .replace(":", "-").substring(0, 19) + ".mp4";
        ReplayBuffer.saveAsync(path, Minecraft.getInstance().getFps());
        NotificationToast.show("Saving clip...");
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        int fps = Math.max(20, mc.getFps());
        if (usesGsr()) {
            GsrReplayBuffer.start(fps, duration.get());
        } else {
            ReplayBuffer.start(fps, duration.get());
        }
    }

    @Override
    public void onDisable() {
        if (usesGsr()) {
            GsrReplayBuffer.stop();
        } else {
            ReplayBuffer.stop();
        }
    }
}
