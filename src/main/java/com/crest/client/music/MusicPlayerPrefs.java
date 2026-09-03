package com.crest.client.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted music player preferences (volume, repeat/shuffle, library folder,
 * pause/duck behavior and the last queue for restore). Stored as Gson JSON in
 * the Fabric config directory, mirroring the waypoint ConfigStorage pattern.
 */
public class MusicPlayerPrefs {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("crest-music.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final MusicPlayerPrefs INSTANCE = new MusicPlayerPrefs();

    public static MusicPlayerPrefs get() { return INSTANCE; }

    private float volume = 100f;
    private String repeatMode = "OFF";
    private boolean shuffle = false;

    private String libraryFolder = "";
    private boolean pauseWithGame = false;
    private boolean ducking = false;
    private float duckVolume = 30f;
    private boolean restoreQueue = true;

    // 0 = toast, 1 = chat, 2 = off
    private int notifyMode = 0;

    private List<String> lastQueue = new ArrayList<>();
    private int lastIndex = -1;

    private transient boolean dirty;

    public float getVolume() { return volume; }
    public void setVolume(float v) { volume = v; dirty = true; }

    public String getRepeatMode() { return repeatMode; }
    public void setRepeatMode(String m) { repeatMode = m; dirty = true; }

    public boolean isShuffle() { return shuffle; }
    public void setShuffle(boolean s) { shuffle = s; dirty = true; }

    public String getLibraryFolder() { return libraryFolder; }
    public void setLibraryFolder(String s) { libraryFolder = s == null ? "" : s; dirty = true; }

    public boolean isPauseWithGame() { return pauseWithGame; }
    public void setPauseWithGame(boolean b) { pauseWithGame = b; dirty = true; }

    public boolean isDucking() { return ducking; }
    public void setDucking(boolean b) { ducking = b; dirty = true; }

    public float getDuckVolume() { return duckVolume; }
    public void setDuckVolume(float v) { duckVolume = Math.max(0f, Math.min(100f, v)); dirty = true; }

    public boolean isRestoreQueue() { return restoreQueue; }
    public void setRestoreQueue(boolean b) { restoreQueue = b; dirty = true; }

    public int getNotifyMode() { return notifyMode; }
    public void setNotifyMode(int m) { notifyMode = ((m % 3) + 3) % 3; dirty = true; }

    public List<String> getLastQueue() { return lastQueue; }
    public int getLastIndex() { return lastIndex; }

    public void queueCaptured(List<String> uris, int index) {
        lastQueue = uris == null ? new ArrayList<>() : new ArrayList<>(uris);
        lastIndex = index;
        dirty = true;
    }

    public boolean isDirty() { return dirty; }
    public void markDirty() { dirty = true; }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try {
            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            MusicPlayerPrefs loaded = GSON.fromJson(json, MusicPlayerPrefs.class);
            if (loaded != null) {
                INSTANCE.volume = loaded.volume;
                INSTANCE.repeatMode = loaded.repeatMode != null ? loaded.repeatMode : "OFF";
                INSTANCE.shuffle = loaded.shuffle;
                INSTANCE.libraryFolder = loaded.libraryFolder != null ? loaded.libraryFolder : "";
                INSTANCE.pauseWithGame = loaded.pauseWithGame;
                INSTANCE.ducking = loaded.ducking;
                INSTANCE.duckVolume = loaded.duckVolume;
                INSTANCE.restoreQueue = loaded.restoreQueue;
                INSTANCE.notifyMode = loaded.notifyMode;
                INSTANCE.lastQueue = loaded.lastQueue != null ? loaded.lastQueue : new ArrayList<>();
                INSTANCE.lastIndex = loaded.lastIndex;
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[Crest Music] Failed to load prefs: " + e);
        }
        INSTANCE.dirty = false;
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(INSTANCE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Crest Music] Failed to save prefs: " + e);
        }
        INSTANCE.dirty = false;
    }

    /** Save only when something changed (called periodically from the client tick). */
    public static void saveIfDirty() {
        if (INSTANCE.dirty) save();
    }
}