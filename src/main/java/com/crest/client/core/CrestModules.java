package com.crest.client.core;

import com.crest.client.core.event.EventBus;
import com.crest.client.core.event.ModuleToggleEvent;
import com.crest.client.core.setting.Setting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CrestModules {
    private static final Map<String, CrestModule> modules = new LinkedHashMap<>();
    private static final Map<String, Boolean> enabledOverrides = new java.util.HashMap<>();
    private static final EventBus eventBus = new EventBus();
    private static final ConfigManager configManager = new ConfigManager();

    // Cached HUD renderables — rebuilt when module enable state changes.
    private static List<RenderableModule> renderableCache = new ArrayList<>();
    private static boolean renderCacheDirty = true;

    public static void init() {
        configManager.load();
    }

    public static void register(CrestModule module) {
        modules.put(module.getId(), module);
        module.onInitialize();

        for (Setting<?> setting : module.getSettings()) {
            setting.bindModule(module.getId());
            setting.load(configManager, module.getId());
        }

        if (module instanceof HudModule hud) {
            var pos = HudSettings.getPosition(module.getId(), hud.getX(), hud.getY());
            hud.setX(pos.x);
            hud.setY(pos.y);
            var size = HudSettings.getSize(module.getId());
            if (size != null) hud.setSize(size.w, size.h);
        }
        module.loadSettings();
        if (configManager.has(module.getId(), "_enabled")) {
            if (!configManager.getBoolean(module.getId(), "_enabled")) {
                enabledOverrides.put(module.getId(), false);
            }
        } else if (!module.isEnabled()) {
            enabledOverrides.put(module.getId(), false);
        }
        renderCacheDirty = true;
        KeybindManager.markDirty();
    }

    public static List<RenderableModule> getRenderableModules() {
        if (renderCacheDirty) {
            renderableCache.clear();
            for (CrestModule mod : modules.values()) {
                if (isEnabled(mod.getId()) && mod instanceof RenderableModule r) {
                    renderableCache.add(r);
                }
            }
            renderCacheDirty = false;
        }
        return renderableCache;
    }

    public static EventBus getEventBus() { return eventBus; }
    public static ConfigManager getConfigManager() { return configManager; }

    public static CrestModule get(String id) {
        return modules.get(id);
    }

    public static Map<String, CrestModule> getAll() {
        return modules;
    }

    public static boolean isEnabled(String id) {
        return enabledOverrides.getOrDefault(id, true);
    }

    public static void setEnabled(String id, boolean enabled) {
        enabledOverrides.put(id, enabled);
        configManager.set(id, "_enabled", enabled);
        CrestModule m = modules.get(id);
        if (m != null) {
            if (enabled) m.onEnable();
            else m.onDisable();
            eventBus.post(new ModuleToggleEvent(m, enabled));
        }
        renderCacheDirty = true;
        if (m != null) {
            NotificationToast.show((enabled ? "\u2713 " : "\u2717 ") + m.getName(),
                enabled ? 0xFF44FF88 : 0xFFFF5B6E);
        }
    }

    public static void toggle(String id) {
        setEnabled(id, !isEnabled(id));
    }

    /** Like {@link #setEnabled}, but without the toast notification (used by editor eye toggles). */
    public static void setEnabledSilent(String id, boolean enabled) {
        enabledOverrides.put(id, enabled);
        configManager.set(id, "_enabled", enabled);
        CrestModule m = modules.get(id);
        if (m != null) {
            if (enabled) m.onEnable();
            else m.onDisable();
            eventBus.post(new ModuleToggleEvent(m, enabled));
        }
        renderCacheDirty = true;
    }

    /** Re-read every setting's persisted value from the config into the live objects. */
    public static void reloadSettings() {
        for (CrestModule mod : modules.values()) {
            for (Setting<?> setting : mod.getSettings()) {
                setting.load(configManager, mod.getId());
            }
            mod.loadSettings();
        }
    }

    /** Curated default-enabled set for first-time users (competitive-friendly, low-risk). */
    private static final String[] DEFAULT_ENABLED = {
        "fps", "coords", "potion_hud", "armor_hud", "cps", "reach", "crosshair",
        "keystrokes", "combo", "scoreboard", "zoom", "chat_timestamp",
        "entity_culling", "no_fog", "no_hurt_cam", "block_outline", "pvp_info",
        "hitbox", "item_counter", "waypoints", "server_address", "toggle_sneak",
        "dynamic_fov", "low_fire", "skin_layers_3d"
    };

    public static void applyDefaults() {
        for (String id : DEFAULT_ENABLED) {
            if (modules.containsKey(id)) setEnabled(id, true);
        }
    }

    public static boolean isFirstRun() {
        return !configManager.has("_meta", "initialized");
    }

    public static void markInitialized() {
        configManager.set("_meta", "initialized", true);
        configManager.save();
    }

    public static List<CrestModule> getByCategory(String category) {
        return modules.values().stream()
            .filter(m -> category.equals(m.getCategory()))
            .collect(Collectors.toList());
    }

    public static List<String> getCategories() {
        return modules.values().stream()
            .map(CrestModule::getCategory)
            .distinct()
            .collect(Collectors.toList());
    }

    public static void notify(String message) {
        NotificationToast.show(message);
    }

    public static void notify(String message, int color) {
        NotificationToast.show(message, color);
    }
    
    public static List<HudModule> getHudModules() {
        List<HudModule> hudModules = new ArrayList<>();
        for (CrestModule module : modules.values()) {
            if (module instanceof HudModule) {
                hudModules.add((HudModule) module);
            }
        }
        return hudModules;
    }
}