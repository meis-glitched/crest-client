package com.crest.client.music.playlist;

import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlaylistManager {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("PlaylistManager");
    private static final Path PLAYLISTS_PATH = FabricLoader.getInstance().getConfigDir().resolve("crest-playlists.json");
    private static final Path BACKUP_PATH = FabricLoader.getInstance().getConfigDir().resolve("crest-playlists.json.bak");
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Instant.class, new InstantAdapter())
        .create();
    
    private final Map<String, Playlist> playlists = new HashMap<>();
    private boolean dirty = false;
    
    public PlaylistManager() {
        load();
    }
    
    public void load() {
        playlists.clear();
        
        if (!PLAYLISTS_PATH.toFile().exists()) {
            LOGGER.info("No playlists file found");
            return;
        }
        
        try (FileReader r = new FileReader(PLAYLISTS_PATH.toFile())) {
            PlaylistData data = GSON.fromJson(r, PlaylistData.class);
            if (data != null && data.playlists != null) {
                for (Playlist p : data.playlists) {
                    playlists.put(p.getId(), p);
                }
                LOGGER.info("Loaded {} playlists", playlists.size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load playlists", e);
        }
    }
    
    public void save() {
        if (!dirty) return;
        dirty = false;
        
        try {
            if (PLAYLISTS_PATH.toFile().exists()) {
                Files.copy(PLAYLISTS_PATH, BACKUP_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            
            PLAYLISTS_PATH.toFile().getParentFile().mkdirs();
            
            PlaylistData data = new PlaylistData();
            data.version = 1;
            data.playlists = new ArrayList<>(playlists.values());
            
            Path tempPath = PLAYLISTS_PATH.getParent().resolve("crest-playlists.json.tmp");
            try (FileWriter w = new FileWriter(tempPath.toFile())) {
                GSON.toJson(data, w);
            }
            
            Files.move(tempPath, PLAYLISTS_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("Saved {} playlists", playlists.size());
            
        } catch (IOException e) {
            LOGGER.error("Failed to save playlists", e);
        }
    }
    
    public Playlist createPlaylist(String name) {
        Playlist playlist = new Playlist(name);
        playlists.put(playlist.getId(), playlist);
        dirty = true;
        LOGGER.info("Created playlist: {}", name);
        return playlist;
    }
    
    public void deletePlaylist(String id) {
        Playlist removed = playlists.remove(id);
        if (removed != null) {
            dirty = true;
            LOGGER.info("Deleted playlist: {}", removed.getName());
        }
    }
    
    public Playlist getPlaylist(String id) {
        return playlists.get(id);
    }
    
    public List<Playlist> getAllPlaylists() {
        return new ArrayList<>(playlists.values());
    }
    
    public void markDirty() {
        dirty = true;
    }
    
    private static class PlaylistData {
        int version;
        List<Playlist> playlists;
    }
    
    private static class InstantAdapter extends com.google.gson.TypeAdapter<Instant> {
        @Override
        public void write(com.google.gson.stream.JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }
        
        @Override
        public Instant read(com.google.gson.stream.JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.parse(in.nextString());
        }
    }
}
