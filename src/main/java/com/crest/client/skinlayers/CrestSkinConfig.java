package com.crest.client.skinlayers;

import com.crest.client.core.CrestModules;
import com.crest.client.core.SkinLayers3dModule;

public final class CrestSkinConfig {

    private CrestSkinConfig() {
    }

    // Effective thickness scalers, driven by the Crest 3D Skin Layers module.
    public static float baseVoxelSize = 1.15f;
    public static float bodyVoxelWidthSize = 1.05f;
    public static float headVoxelSize = 1.18f;
    public static float firstPersonPixelScaling = 1.1f;
    public static float skullVoxelSize = 1.1f;

    // Layer enable flags follow the module's per-layer toggles.
    public static boolean enableHat = true;
    public static boolean enableJacket = true;
    public static boolean enableLeftSleeve = true;
    public static boolean enableRightSleeve = true;
    public static boolean enableLeftPants = true;
    public static boolean enableRightPants = true;

    // Distance (in blocks) after which layers are not rendered, to save perf.
    public static int renderDistanceLOD = 14;

    // fastRender bakes one opaque box per layer for the "core", matching tr7zw.
    public static boolean fastRender = true;

    public static boolean compatibilityMode = true;

    public static boolean isEnabled(String layer) {
        SkinLayers3dModule mod = SkinLayers3dModule.getInstance();
        return mod != null && CrestModules.isEnabled("skin_layers_3d") && mod.isLayerEnabled(layer);
    }

    public static void refresh() {
        // Clamp thickness-driven scaling to a proportional, non-distorted range.
        // Unbounded, a maxed slider (1.0) inflates the mesh ~1.6x with pixels up to
        // 8 deep, so the model turns into a bloated, overlapping (torn) mess.
        float body = clamp(SkinLayers3dModule.getBodyThickness(), 0f, 0.5f);
        float head = clamp(SkinLayers3dModule.getHeadThickness(), 0f, 0.4f);
        float arms = clamp(SkinLayers3dModule.getArmsThickness(), 0f, 0.3f);
        baseVoxelSize = 1.0f + body * 0.6f;
        bodyVoxelWidthSize = 1.0f + body * 0.25f;
        headVoxelSize = 1.0f + head * 0.6f;
        firstPersonPixelScaling = 1.0f + arms * 0.4f;
        skullVoxelSize = headVoxelSize;
        enableHat = isEnabled("hat");
        enableJacket = isEnabled("jacket");
        enableLeftSleeve = isEnabled("leftSleeve");
        enableRightSleeve = isEnabled("rightSleeve");
        enableLeftPants = isEnabled("leftPants");
        enableRightPants = isEnabled("rightPants");
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

}
