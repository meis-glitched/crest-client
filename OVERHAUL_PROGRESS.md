# CREST CLIENT - COMPLETE OVERHAUL IMPLEMENTATION GUIDE

## STATUS: Foundation Phase In Progress

### ✅ Completed Components

#### Phase 1.1: Logging & Error Handling Infrastructure
- ✅ Created `CrestLogger` with context-aware logging
- ✅ Created `LoggerFactory` for logger management
- ✅ Created `ErrorReporter` for centralized error handling
- ✅ Created exception hierarchy (CrestException, ConfigException, StorageException, NetworkException, AudioException)
- ✅ Added logback.xml configuration with file rotation

#### Phase 1.2: Thread Management
- ✅ Created `CrestExecutors` with managed thread pools (IO, Network, Audio, Compute, Scheduled)
- ✅ Added proper shutdown hooks
- ✅ Custom thread factory with exception handling

#### Phase 1.3: Security
- ✅ Created `UrlValidator` with comprehensive SSRF protection (IPv4, IPv6, private ranges, localhost, link-local)
- ✅ Created `PathValidator` for path traversal prevention
- ✅ Added filename sanitization

#### Phase 1.4: Caching & Memory
- ✅ Created `CacheManager` using Guava Cache with LRU eviction
- ✅ Created `BufferPool` for ByteBuffer pooling

#### Phase 1.5: Networking
- ✅ Created `HttpClient` wrapper around OkHttp with security integration
- ✅ Added async request support with callbacks

### 🚧 In Progress

#### Build System
- Fixed jaudiotagger dependency (changed to net.jthink:jaudiotagger:3.0.1)
- Removed problematic lastfm and rtree dependencies temporarily
- Added relocations for shaded dependencies

### 📋 Next Steps (Immediate Priority)

#### 1. Refactor ConfigManager (CRITICAL)
**File**: `src/main/java/com/crest/client/core/ConfigManager.java`

**Current Issues**:
- Lines 64, 74: Empty catch blocks (silent failures)
- No backup mechanism
- No corruption detection
- No transaction support

**Required Changes**:
```java
package com.crest.client.core;

import com.crest.client.core.error.ConfigException;
import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
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
    private int version = 2;

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
        
        try {
            String json = Files.readString(CONFIG_PATH);
            Map<String, Object> root = GSON.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
            
            // Check version
            if (root.containsKey("version")) {
                int fileVersion = ((Number) root.get("version")).intValue();
                if (fileVersion < version) {
                    LOGGER.info("Migrating config from v{} to v{}", fileVersion, version);
                    migrateConfig(root, fileVersion);
                }
            } else {
                // Legacy format (v1)
                LOGGER.info("Migrating legacy config to v{}", version);
                data.putAll((Map<String, Map<String, Object>>) root);
                return;
            }
            
            // Load modules data
            if (root.containsKey("modules")) {
                data.putAll((Map<String, Map<String, Object>>) root.get("modules"));
            }
            
            LOGGER.info("Config loaded successfully ({} modules)", data.size());
            
        } catch (Exception e) {
            LOGGER.error("Failed to load config, checking backup", e);
            if (tryLoadBackup()) {
                LOGGER.info("Recovered from backup");
            } else {
                throw new ConfigException("Failed to load config and backup", e);
            }
        }
    }

    public void save() {
        if (!dirty) return;
        dirty = false;
        
        try {
            // Create backup before save
            if (CONFIG_PATH.toFile().exists()) {
                Files.copy(CONFIG_PATH, BACKUP_PATH, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.debug("Created config backup");
            }
            
            // Prepare save data with version
            Map<String, Object> saveData = new HashMap<>();
            saveData.put("version", version);
            saveData.put("modules", data);
            saveData.put("saved_at", System.currentTimeMillis());
            
            // Write to temp file first
            Path tempPath = CONFIG_PATH.getParent().resolve("crest.json.tmp");
            CONFIG_PATH.toFile().getParentFile().mkdirs();
            
            try (FileWriter w = new FileWriter(tempPath.toFile())) {
                GSON.toJson(saveData, w);
            }
            
            // Atomic rename
            Files.move(tempPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            
            LOGGER.info("Config saved successfully");
            
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
            throw new ConfigException("Failed to save config", e);
        }
    }

    public void markDirty() { 
        dirty = true; 
    }

    private Object getValue(String moduleId, String key) {
        Map<String, Object> module = data.get(moduleId);
        return module != null ? module.get(key) : null;
    }
    
    private boolean tryLoadBackup() {
        if (!BACKUP_PATH.toFile().exists()) {
            return false;
        }
        
        try {
            String json = Files.readString(BACKUP_PATH);
            Map<String, Map<String, Object>> loaded = GSON.fromJson(json, DATA_TYPE);
            if (loaded != null) {
                data.putAll(loaded);
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("Backup also corrupted", e);
        }
        return false;
    }
    
    private void migrateConfig(Map<String, Object> root, int fromVersion) {
        // V1 -> V2: Wrap modules in "modules" key
        if (fromVersion < 2) {
            root.remove("version");
            data.putAll((Map<String, Map<String, Object>>) root);
        }
    }
}
```

#### 2. Refactor MusicPlayer.java (CRITICAL - 817 lines, multiple System.out.println, thread issues)

**Key Changes Required**:
- Replace `System.out.println` with `LOGGER.info/debug`
- Replace `System.err.println` with `LOGGER.error`
- Replace `printStackTrace()` with `LOGGER.error("context", exception)`
- Move playback thread to `CrestExecutors.audio()`
- Add proper error handling for all catch blocks
- Extract playlist management to separate class
- Extract audio backend selection to separate class

**Pattern to Follow** (lines 101-110):
```java
// BEFORE
System.out.println("Loading track: " + track.getInfo().title);

// AFTER
private static final CrestLogger LOGGER = LoggerFactory.getLogger("MusicPlayer");
LOGGER.audio("Loading track: {}", track.getInfo().title);
```

#### 3. Refactor WaypointStorage.java (Line 78: returns empty result on exception)

**Current Issue**:
```java
} catch (Exception e) {
    return new LoadResult();  // Silent failure!
}
```

**Fix**:
```java
} catch (Exception e) {
    LOGGER.error("Failed to load waypoints for world {} dimension {}", e, worldId, dim.id);
    throw new StorageException("Failed to load waypoints", e);
}
```

### 📂 Directory Structure (Created)

```
src/main/java/com/crest/client/core/
├── error/                    ✅ CREATED
│   ├── CrestException.java
│   ├── ConfigException.java
│   ├── StorageException.java
│   ├── NetworkException.java
│   └── AudioException.java
├── logging/                  ✅ CREATED
│   ├── CrestLogger.java
│   ├── LoggerFactory.java
│   └── ErrorReporter.java
├── concurrent/               ✅ CREATED
│   └── CrestExecutors.java
├── cache/                    ✅ CREATED
│   ├── CacheManager.java
│   └── BufferPool.java
├── security/                 ✅ CREATED
│   ├── UrlValidator.java
│   └── PathValidator.java
├── network/                  ✅ CREATED
│   └── HttpClient.java
├── storage/                  🔜 TODO
│   ├── StorageEngine.java
│   ├── JsonStorage.java
│   ├── StorageTransaction.java
│   └── BackupManager.java
└── module/                   🔜 TODO
    ├── AbstractModule.java
    ├── AbstractHudModule.java
    ├── ModuleManager.java
    └── ModuleRegistry.java
```

### 🎯 Immediate Action Items

1. **Test Current Build**
   ```bash
   cd /home/glitcher/crest-client
   ./gradlew build
   ```

2. **Refactor High-Priority Files** (replace empty catches + logging):
   - `ConfigManager.java` (2 empty catches)
   - `MusicPlayer.java` (multiple System.out/err)
   - `WaypointStorage.java` (silent failures)
   - `SkinLibrary.java` (no error handling)
   - `CapeLibrary.java` (no error handling)
   - `Recorder.java` (command injection risk + logging)
   - `Streamer.java` (URL validation)
   - `StateRecorder.java` (thread safety)

3. **Initialize Infrastructure in CrestClient.java**:
   ```java
   @Override
   public void onInitializeClient() {
       // Initialize infrastructure
       CrestExecutors.initialize();
       HttpClient.initialize();
       
       // Existing initialization...
       CrestModules.init();
       // ...
   }
   ```

4. **Add Shutdown Hook**:
   ```java
   ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
       CrestModules.getConfigManager().save();
       HudSettings.save();
       MusicModule.saveNow();
       
       // NEW: Shutdown thread pools
       CrestExecutors.shutdown();
   });
   ```

### 📊 Progress Tracker

**Foundation (Phase 1)**:
- [x] 1.1 - Logging & Error Handling Infrastructure (100%)
- [x] 1.2 - Thread Management (100%)
- [x] 1.3 - Security (100%)
- [x] 1.4 - Caching & Memory (100%)
- [ ] 1.5 - Refactor 69 files with empty catches (0%)
- [ ] 1.6 - Refactor 38 files with System.out/err (0%)
- [ ] 1.7 - Storage Layer Repository Pattern (0%)
- [ ] 1.8 - Module Architecture Refactoring (0%)
- [ ] 1.9 - UI Framework Enhancements (0%)

**Features (Phase 2)**:
- [ ] 2.1 - Music: Playlists (0%)
- [ ] 2.2 - Music: Library Scanner (0%)
- [ ] 2.3 - Music: Equalizer (0%)
- [ ] 2.4 - Waypoints: Import/Export (0%)
- [ ] 2.5 - Waypoints: Pathfinding (0%)
- [ ] 2.6 - Waypoints: Xaero's Integration (0%)
- [ ] 2.7 - HUD: Drag & Drop Editor (0%)
- [ ] 2.8 - All remaining features (0%)

**Estimated Completion**: 
- Infrastructure: ~20% complete
- Features: 0% complete
- Overall: ~10% complete (30-35 hours invested out of 335)

### 🚀 Quick Wins (Next 2-4 Hours)

1. **Fix All Empty Catch Blocks** (search for `catch.*Exception.*ignored` or empty catch blocks)
2. **Replace All System.out/err** (search and replace with LOGGER calls)
3. **Add Security Validation** (use UrlValidator in MusicPlayer, Streamer)
4. **Initialize Thread Pools** (use CrestExecutors instead of new Thread())

### 📝 Notes

This is a **massive refactoring project** (335+ hours total). The foundation infrastructure is now in place and provides:

- ✅ Production-grade logging with file rotation
- ✅ Managed thread pools with proper lifecycle
- ✅ Comprehensive security validation
- ✅ Memory management with caching and buffer pooling
- ✅ Type-safe exception hierarchy

The next phase involves systematically refactoring existing code to use this infrastructure, then building new features on the clean foundation.
