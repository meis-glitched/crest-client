package com.crest.client.core.hud.profile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HudProfile {
    
    public static class ModuleSettings {
        public boolean enabled;
        public int x;
        public int y;
        public float scale;
        public float opacity;
        public boolean backgroundEnabled;
        public int backgroundColor;
        public float backgroundOpacity;
        public boolean borderEnabled;
        public int borderColor;
        public float borderThickness;
        public boolean shadowEnabled;
        public int shadowColor;
        public float shadowBlur;
        public int shadowOffsetX;
        public int shadowOffsetY;
        public int fontSize;
        public int anchor;
        
        public ModuleSettings() {
            this.enabled = true;
            this.x = 0;
            this.y = 0;
            this.scale = 1.0f;
            this.opacity = 1.0f;
            this.backgroundEnabled = true;
            this.backgroundColor = 0x000000;
            this.backgroundOpacity = 0.5f;
            this.borderEnabled = false;
            this.borderColor = 0xFFFFFF;
            this.borderThickness = 1.0f;
            this.shadowEnabled = true;
            this.shadowColor = 0x000000;
            this.shadowBlur = 2.0f;
            this.shadowOffsetX = 1;
            this.shadowOffsetY = 1;
            this.fontSize = 12;
            this.anchor = 4;
        }
        
        public ModuleSettings copy() {
            ModuleSettings copy = new ModuleSettings();
            copy.enabled = this.enabled;
            copy.x = this.x;
            copy.y = this.y;
            copy.scale = this.scale;
            copy.opacity = this.opacity;
            copy.backgroundEnabled = this.backgroundEnabled;
            copy.backgroundColor = this.backgroundColor;
            copy.backgroundOpacity = this.backgroundOpacity;
            copy.borderEnabled = this.borderEnabled;
            copy.borderColor = this.borderColor;
            copy.borderThickness = this.borderThickness;
            copy.shadowEnabled = this.shadowEnabled;
            copy.shadowColor = this.shadowColor;
            copy.shadowBlur = this.shadowBlur;
            copy.shadowOffsetX = this.shadowOffsetX;
            copy.shadowOffsetY = this.shadowOffsetY;
            copy.fontSize = this.fontSize;
            copy.anchor = this.anchor;
            return copy;
        }
    }
    
    private final String id;
    private String name;
    private String description;
    private final Map<String, ModuleSettings> modules;
    private long createdAt;
    private long modifiedAt;
    
    public HudProfile(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.description = "";
        this.modules = new HashMap<>();
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = this.createdAt;
    }
    
    public HudProfile(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.modules = new HashMap<>();
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = this.createdAt;
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { 
        this.name = name;
        touch();
    }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { 
        this.description = description;
        touch();
    }
    
    public long getCreatedAt() { return createdAt; }
    public long getModifiedAt() { return modifiedAt; }
    
    public ModuleSettings getModuleSettings(String moduleId) {
        return modules.computeIfAbsent(moduleId, k -> new ModuleSettings());
    }
    
    public void setModuleSettings(String moduleId, ModuleSettings settings) {
        modules.put(moduleId, settings);
        touch();
    }
    
    public Map<String, ModuleSettings> getAllModuleSettings() {
        return new HashMap<>(modules);
    }
    
    public HudProfile copy(String newName) {
        HudProfile copy = new HudProfile(newName);
        copy.description = this.description;
        for (Map.Entry<String, ModuleSettings> entry : this.modules.entrySet()) {
            copy.modules.put(entry.getKey(), entry.getValue().copy());
        }
        return copy;
    }
    
    private void touch() {
        this.modifiedAt = System.currentTimeMillis();
    }
}
