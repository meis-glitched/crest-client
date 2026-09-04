package com.crest.client.core.hud.profile;

import com.crest.client.core.CrestModules;
import com.crest.client.core.HudModule;
import com.crest.client.core.error.StorageException;
import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class HudProfileManager {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("HudProfileManager");
    private static final Path PROFILES_DIR = FabricLoader.getInstance().getConfigDir().resolve("crest/hud_profiles");
    private static final Path CURRENT_PROFILE_FILE = PROFILES_DIR.resolve("current.txt");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final Map<String, HudProfile> profiles = new LinkedHashMap<>();
    private HudProfile currentProfile;
    private static HudProfileManager instance;
    
    private HudProfileManager() {
        try {
            Files.createDirectories(PROFILES_DIR);
        } catch (IOException e) {
            LOGGER.error("Failed to create profiles directory", e);
        }
    }
    
    public static HudProfileManager getInstance() {
        if (instance == null) {
            instance = new HudProfileManager();
        }
        return instance;
    }
    
    public void load() {
        profiles.clear();
        
        if (!Files.exists(PROFILES_DIR)) {
            LOGGER.info("No profiles directory found, creating default profile");
            createDefaultProfile();
            return;
        }
        
        try {
            Files.list(PROFILES_DIR)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(this::loadProfile);
            
            if (profiles.isEmpty()) {
                LOGGER.info("No profiles found, creating default");
                createDefaultProfile();
            }
            
            String currentId = loadCurrentProfileId();
            currentProfile = profiles.get(currentId);
            if (currentProfile == null) {
                currentProfile = profiles.values().iterator().next();
                LOGGER.info("Current profile not found, using: {}", currentProfile.getName());
            } else {
                LOGGER.info("Loaded current profile: {}", currentProfile.getName());
            }
            
            applyProfile(currentProfile);
            
        } catch (IOException e) {
            LOGGER.error("Failed to load profiles", e);
            createDefaultProfile();
        }
    }
    
    public void save() {
        if (currentProfile != null) {
            captureCurrentState(currentProfile);
            saveProfile(currentProfile);
            saveCurrentProfileId(currentProfile.getId());
        }
    }
    
    public HudProfile createProfile(String name) {
        HudProfile profile = new HudProfile(name);
        captureCurrentState(profile);
        profiles.put(profile.getId(), profile);
        saveProfile(profile);
        LOGGER.info("Created profile: {}", name);
        return profile;
    }
    
    public void deleteProfile(String id) {
        if (profiles.size() <= 1) {
            LOGGER.warn("Cannot delete last profile");
            return;
        }
        
        HudProfile profile = profiles.remove(id);
        if (profile != null) {
            Path file = PROFILES_DIR.resolve(id + ".json");
            try {
                Files.deleteIfExists(file);
                LOGGER.info("Deleted profile: {}", profile.getName());
                
                if (currentProfile == profile) {
                    currentProfile = profiles.values().iterator().next();
                    applyProfile(currentProfile);
                    saveCurrentProfileId(currentProfile.getId());
                }
            } catch (IOException e) {
                LOGGER.error("Failed to delete profile file", e);
            }
        }
    }
    
    public HudProfile duplicateProfile(String sourceId, String newName) {
        HudProfile source = profiles.get(sourceId);
        if (source == null) {
            LOGGER.error("Source profile not found: {}", sourceId);
            return null;
        }
        
        HudProfile copy = source.copy(newName);
        profiles.put(copy.getId(), copy);
        saveProfile(copy);
        LOGGER.info("Duplicated profile {} -> {}", source.getName(), newName);
        return copy;
    }
    
    public void switchProfile(String id) {
        HudProfile profile = profiles.get(id);
        if (profile == null) {
            LOGGER.error("Profile not found: {}", id);
            return;
        }
        
        if (currentProfile != null) {
            captureCurrentState(currentProfile);
            saveProfile(currentProfile);
        }
        
        currentProfile = profile;
        applyProfile(profile);
        saveCurrentProfileId(id);
        LOGGER.info("Switched to profile: {}", profile.getName());
    }
    
    public HudProfile getCurrentProfile() {
        return currentProfile;
    }
    
    public Collection<HudProfile> getAllProfiles() {
        return new ArrayList<>(profiles.values());
    }
    
    public HudProfile getProfile(String id) {
        return profiles.get(id);
    }
    
    private void createDefaultProfile() {
        HudProfile defaultProfile = new HudProfile("Default");
        defaultProfile.setDescription("Default HUD layout");
        captureCurrentState(defaultProfile);
        profiles.put(defaultProfile.getId(), defaultProfile);
        currentProfile = defaultProfile;
        saveProfile(defaultProfile);
        saveCurrentProfileId(defaultProfile.getId());
    }
    
    private void captureCurrentState(HudProfile profile) {
        for (HudModule module : CrestModules.getHudModules()) {
            HudProfile.ModuleSettings settings = new HudProfile.ModuleSettings();
            settings.enabled = module.isEnabled();
            settings.x = module.getX();
            settings.y = module.getY();
            settings.scale = 1.0f;
            settings.opacity = 1.0f;
            profile.setModuleSettings(module.getId(), settings);
        }
    }
    
    private void applyProfile(HudProfile profile) {
        for (HudModule module : CrestModules.getHudModules()) {
            HudProfile.ModuleSettings settings = profile.getModuleSettings(module.getId());
            if (settings.enabled) {
                CrestModules.setEnabled(module.getId(), true);
            } else {
                CrestModules.setEnabled(module.getId(), true);
            }
            module.setX(settings.x); module.setY(settings.y);
        }
    }
    
    private void loadProfile(Path file) {
        try {
            String json = Files.readString(file);
            ProfileData data = GSON.fromJson(json, ProfileData.class);
            
            HudProfile profile = new HudProfile(data.id, data.name, data.description);
            for (Map.Entry<String, HudProfile.ModuleSettings> entry : data.modules.entrySet()) {
                profile.setModuleSettings(entry.getKey(), entry.getValue());
            }
            
            profiles.put(profile.getId(), profile);
            LOGGER.info("Loaded profile: {}", profile.getName());
            
        } catch (Exception e) {
            LOGGER.error("Failed to load profile from {}", e, file.getFileName());
        }
    }
    
    private void saveProfile(HudProfile profile) {
        Path file = PROFILES_DIR.resolve(profile.getId() + ".json");
        Path temp = PROFILES_DIR.resolve(profile.getId() + ".json.tmp");
        
        try {
            ProfileData data = new ProfileData();
            data.id = profile.getId();
            data.name = profile.getName();
            data.description = profile.getDescription();
            data.createdAt = profile.getCreatedAt();
            data.modifiedAt = profile.getModifiedAt();
            data.modules = profile.getAllModuleSettings();
            
            String json = GSON.toJson(data);
            Files.writeString(temp, json);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            
        } catch (IOException e) {
            LOGGER.error("Failed to save profile: {}", e, profile.getName());
            throw new StorageException("Failed to save profile", e);
        }
    }
    
    private String loadCurrentProfileId() {
        if (!Files.exists(CURRENT_PROFILE_FILE)) {
            return null;
        }
        try {
            return Files.readString(CURRENT_PROFILE_FILE).trim();
        } catch (IOException e) {
            LOGGER.error("Failed to read current profile ID", e);
            return null;
        }
    }
    
    private void saveCurrentProfileId(String id) {
        try {
            Files.writeString(CURRENT_PROFILE_FILE, id);
        } catch (IOException e) {
            LOGGER.error("Failed to save current profile ID", e);
        }
    }
    
    private static class ProfileData {
        String id;
        String name;
        String description;
        long createdAt;
        long modifiedAt;
        Map<String, HudProfile.ModuleSettings> modules;
    }
}
