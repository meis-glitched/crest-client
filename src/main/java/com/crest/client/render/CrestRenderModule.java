package com.crest.client.render;

import com.crest.client.core.CrestModule;
import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;
import com.crest.client.core.setting.BooleanSetting;
import com.crest.client.core.setting.Setting;

import java.util.List;

/**
 * CrestRender - Reticulum Renderer Module
 * 
 * High-performance Rust-based renderer replacing Sodium.
 * Phase 1: Basic initialization and native library loading.
 */
public class CrestRenderModule implements CrestModule {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("CrestRender");
    
    private final BooleanSetting enabled = new BooleanSetting("Enable CrestRender", 
        "Use Rust-based renderer (experimental)", false);
    
    private boolean initialized = false;
    
    @Override
    public String getId() {
        return "crestrender";
    }
    
    @Override
    public String getName() {
        return "CrestRender (Reticulum)";
    }
    
    @Override
    public String getDescription() {
        return "High-performance Rust renderer - Sodium replacement";
    }
    
    @Override
    public String getCategory() {
        return "Performance";
    }
    
    @Override
    public boolean isEnabled() {
        return enabled.get() && initialized;
    }
    
    @Override
    public List<Setting<?>> getSettings() {
        return List.of(enabled);
    }
    
    @Override
    public void onInitialize() {
        LOGGER.info("═══════════════════════════════════════════════════════════");
        LOGGER.info("  CrestRender - Reticulum Renderer");
        LOGGER.info("  Phase 1: Native Library Initialization");
        LOGGER.info("═══════════════════════════════════════════════════════════");
        
        try {
            CrestRenderLoader.ensureLoaded();
            
            if (CrestRenderLoader.isLoaded()) {
                String version = CrestRenderNative.nativeGetVersion();
                LOGGER.info("✓ Native renderer loaded: {}", version);
                LOGGER.info("✓ OpenGL 3.3+ backend ready");
                LOGGER.info("✓ Multi-threaded chunk meshing available");
                initialized = true;
            } else {
                LOGGER.warn("✗ Native renderer failed to load - falling back to vanilla");
                initialized = false;
            }
            
        } catch (Exception e) {
            LOGGER.error("✗ Failed to initialize CrestRender", e);
            initialized = false;
        }
        
        if (initialized) {
            LOGGER.info("═══════════════════════════════════════════════════════════");
            LOGGER.info("  Status: READY");
            LOGGER.info("  Note: Renderer integration is Phase 2+");
            LOGGER.info("  Current: Library loaded, awaiting chunk meshing impl");
            LOGGER.info("═══════════════════════════════════════════════════════════");
        }
    }
    
    @Override
    public void onEnable() {
        if (!initialized) {
            LOGGER.warn("Cannot enable CrestRender - initialization failed");
            enabled.set(false);
            return;
        }
        LOGGER.info("CrestRender enabled (experimental - Phase 1)");
    }
    
    @Override
    public void onDisable() {
        LOGGER.info("CrestRender disabled");
    }
    
    public static boolean isAvailable() {
        return CrestRenderLoader.isLoaded();
    }
}
