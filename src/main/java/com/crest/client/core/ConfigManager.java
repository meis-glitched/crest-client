package com.crest.client.core;

import com.crest.client.core.error.ConfigException;
import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("ConfigManager");
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("crest.json");
    private static final Path BACKUP_PATH = FabricLoader.getInstance().getConfigDir().resolve("crest.json.bak");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();

    private final Map<String, Map<String, Object>> data = new HashMap<>();
    private boolean dirty;
    private static final int VERSION = 2;

    public boolean has(String moduleId, String key) {
        Map<String, Object> module = data.get(moduleId);
        return module != null && module.containsKey(key);
    }

    public boolean getBoolean(String moduleId, String key) {
        Object v = getValue(moduleId, key);
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return false;
    }

    public int getInt(String moduleId, String key) {
        Object v = getValue(moduleId, key);
        if (v instanceof Number n) return n.intValue();
        return 0;
    }

    public float getFloat(String moduleId, String key) {
        Object v = getValue(moduleId, key);
        if (v instanceof Number n) return n.floatValue();
        return 0f;
    }

    public String getString(String moduleId, String key) {
        Object v = getValue(moduleId, key);
        return v instanceof String s ? s : null;
    }

    public <T> void set(String moduleId, String key, T value) {
        data.computeIfAbsent(moduleId, k -> new HashMap<>()).put(key, value);
        dirty = true;
    }

    public void load() {
        data.clear();
        
        if (!CONFIG_PATH.toFile().exists()) {
            LOGGER.info("No config file found, starting fresh");
            return;
        }
        
        try (FileReader r = new FileReader(CONFIG_PATH.toFile())) {
            Map<String, Map<String, Object>> loaded = GSON.fromJson(r, DATA_TYPE);
            if (loaded != null) {
                data.putAll(loaded);
                LOGGER.info("Config loaded successfully ({} modules)", data.size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load config, attempting backup recovery", e);
            if (tryLoadBackup()) {
                LOGGER.info("Successfully recovered config from backup");
            } else {
                LOGGER.error("Config and backup both failed, starting with empty config");
            }
        }
    }

    public void save() {
        if (!dirty) return;
        dirty = false;
        
        try {
            if (CONFIG_PATH.toFile().exists()) {
                Files.copy(CONFIG_PATH, BACKUP_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            
            CONFIG_PATH.toFile().getParentFile().mkdirs();
            
            Path tempPath = CONFIG_PATH.getParent().resolve("crest.json.tmp");
            try (FileWriter w = new FileWriter(tempPath.toFile())) {
                GSON.toJson(data, w);
            }
            
            Files.move(tempPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("Config saved successfully");
            
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
            throw new ConfigException("Failed to save config", e);
        }
    }

    public void markDirty() { dirty = true; }

    private Object getValue(String moduleId, String key) {
        Map<String, Object> module = data.get(moduleId);
        return module != null ? module.get(key) : null;
    }
    
    private boolean tryLoadBackup() {
        if (!BACKUP_PATH.toFile().exists()) {
            return false;
        }
        
        try (FileReader r = new FileReader(BACKUP_PATH.toFile())) {
            Map<String, Map<String, Object>> loaded = GSON.fromJson(r, DATA_TYPE);
            if (loaded != null) {
                data.putAll(loaded);
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("Backup file also corrupted", e);
        }
        return false;
    }
}
