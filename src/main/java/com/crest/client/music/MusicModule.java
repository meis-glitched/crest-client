package com.crest.client.music;

import com.crest.client.core.NotificationToast;
import com.crest.client.core.event.TickEvent;
import com.crest.client.core.CrestModules;
import com.crest.client.ui.Theme;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

public class MusicModule {
    private static MusicPlayer player;
    private static Consumer<TickEvent> tickListener;

    // Behavior controller state.
    private static boolean lastIdle;
    private static boolean autoPaused;
    private static int saveCounter;

    public static void init() {
        MusicPlayerPrefs.load();
        player = new MusicPlayer();

        // Apply persisted playback settings.
        player.setSliderVolume(MusicPlayerPrefs.get().getVolume());
        try {
            player.setRepeatMode(MusicPlayer.RepeatMode.valueOf(MusicPlayerPrefs.get().getRepeatMode()));
        } catch (IllegalArgumentException e) {
            player.setRepeatMode(MusicPlayer.RepeatMode.OFF);
        }
        player.setShuffle(MusicPlayerPrefs.get().isShuffle());

        // Queue persistence: capture URIs + index whenever the queue changes.
        player.setOnQueueChange(mp -> {
            MusicPlayerPrefs.get().queueCaptured(mp.getQueueUris(), mp.getQueueIndex());
        });

        player.setOnTrackStart(mp -> {
            var info = mp.getCurrentTrack().getInfo();
            String label = (info.title != null ? info.title : "Unknown")
                + " - " + (info.author != null ? info.author : "Unknown");
            String msg = "Playing: " + label;
            System.out.println("[Crest Music] " + msg);

            // Now-playing notification (toast/chat/off). onTrackStart fires from
            // the Lavaplayer audio thread, so any state-touching dispatch must be
            // pushed onto the render thread via mc.execute.
            final String shortLabel = label;
            Minecraft mc = Minecraft.getInstance();
            int mode = MusicPlayerPrefs.get().getNotifyMode();
            if (mode == 0) {
                mc.execute(() -> NotificationToast.show("♫ " + shortLabel, Theme.getAnimatedAccent()));
            } else if (mode == 1 && mc.player != null) {
                mc.execute(() -> mc.player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("[" + msg + "]")));
            }

            MusicScreen screen = currentScreen();
            if (screen != null) screen.setStatus(msg);
        });

        player.setOnTrackEnd(mp -> {
            MusicScreen screen = currentScreen();
            if (screen != null) screen.setStatus("Track ended");
        });

        player.setOnError(msg -> {
            MusicScreen screen = currentScreen();
            if (screen != null) screen.setStatus(msg);
        });

        tickListener = MusicModule::onTick;
        CrestModules.getEventBus().subscribe(TickEvent.class, tickListener);

        if (MusicPlayerPrefs.get().isRestoreQueue()) {
            player.restoreQueue(MusicPlayerPrefs.get().getLastQueue(), MusicPlayerPrefs.get().getLastIndex());
        }
    }

    public static MusicPlayer getPlayer() {
        return player;
    }

    public static MusicPlayerPrefs getPrefs() {
        return MusicPlayerPrefs.get();
    }

    /** Re-capture the current playback settings into prefs and mark them dirty. */
    public static void syncPrefs() {
        MusicPlayerPrefs p = MusicPlayerPrefs.get();
        p.setVolume(player.getSliderVolume());
        p.setRepeatMode(player.getRepeatMode().name());
        p.setShuffle(player.isShuffle());
    }

    public static void saveNow() {
        syncPrefs();
        MusicPlayerPrefs.save();
    }

    /** Re-evaluate pause/duck immediately (used when toggles change in the UI). */
    public static void refreshIdleState() {
        Minecraft mc = Minecraft.getInstance();
        boolean idle = mc.hasSingleplayerServer() && mc.isPaused();
        if (idle == lastIdle) {
            if (idle) applyDuck();
            else removeDuck();
            return;
        }
        lastIdle = idle;
        if (idle) onIdleStart();
        else onIdleEnd();
    }

    private static void onTick(TickEvent event) {
        if (player == null) return;

        refreshIdleState();

        // Debounced save (~10s after the last change).
        if (MusicPlayerPrefs.get().isDirty()) {
            if (++saveCounter >= 200) {
                saveCounter = 0;
                MusicPlayerPrefs.saveIfDirty();
            }
        } else {
            saveCounter = 0;
        }
    }

    private static void onIdleStart() {
        MusicPlayerPrefs p = MusicPlayerPrefs.get();
        if (p.isPauseWithGame() && player.isPlaying()) {
            autoPaused = true;
            player.pause();
        }
        applyDuck();
    }

    private static void onIdleEnd() {
        if (autoPaused) {
            autoPaused = false;
            player.play();
        }
        removeDuck();
    }

    private static void applyDuck() {
        MusicPlayerPrefs p = MusicPlayerPrefs.get();
        if (p.isDucking()) {
            float f = p.getDuckVolume() / 100f;
            if (player.getVolumeFactor() != f) player.setVolumeFactor(f);
        }
    }

    private static void removeDuck() {
        if (player.getVolumeFactor() != 1f) player.setVolumeFactor(1f);
    }

    private static MusicScreen currentScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MusicScreen ms) return ms;
        return null;
    }
}