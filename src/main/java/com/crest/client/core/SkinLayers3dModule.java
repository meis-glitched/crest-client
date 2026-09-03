package com.crest.client.core;

import com.crest.client.core.setting.BooleanSetting;
import com.crest.client.core.setting.FloatSetting;
import com.crest.client.core.setting.Setting;
import com.crest.client.core.setting.SettingGroup;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class SkinLayers3dModule implements CrestModule {
    private final BooleanSetting enabled = new BooleanSetting("Enabled", true);
    private final FloatSetting headThickness = new FloatSetting("Head Thickness", 0f, 1f, 0.4f);
    private final FloatSetting bodyThickness = new FloatSetting("Body Thickness", 0f, 1f, 0.5f);
    private final FloatSetting armsThickness = new FloatSetting("Arms Thickness", 0f, 1f, 0.3f);
    private final FloatSetting legsThickness = new FloatSetting("Legs Thickness", 0f, 1f, 0.3f);

    private final BooleanSetting hat = new BooleanSetting("Hat Layer", true);
    private final BooleanSetting jacket = new BooleanSetting("Jacket Layer", true);
    private final BooleanSetting leftSleeve = new BooleanSetting("Left Sleeve", true);
    private final BooleanSetting rightSleeve = new BooleanSetting("Right Sleeve", true);
    private final BooleanSetting leftPants = new BooleanSetting("Left Pants", true);
    private final BooleanSetting rightPants = new BooleanSetting("Right Pants", true);

    public static final Set<Item> HIDE_HEAD_LAYERS = Set.of(Items.ZOMBIE_HEAD, Items.CREEPER_HEAD,
            Items.DRAGON_HEAD, Items.SKELETON_SKULL, Items.WITHER_SKELETON_SKULL);

    public static SkinLayers3dModule INSTANCE;

    @Override public String getId() { return "skin_layers_3d"; }
    @Override public String getName() { return "3D Skin Layers"; }
    @Override public String getDescription() { return "Gives player outer skin layers actual 3D thickness."; }
    @Override public String getCategory() { return "Visual"; }
    @Override public boolean isEnabled() { return true; }

    @Override
    public void onInitialize() { INSTANCE = this; }

    /** Resolve the module from the registry, falling back to the cached instance. */
    public static SkinLayers3dModule getInstance() {
        var m = INSTANCE;
        if (m != null) return m;
        return CrestModules.get("skin_layers_3d") instanceof SkinLayers3dModule mod ? mod : null;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(enabled, headThickness, bodyThickness, armsThickness, legsThickness,
                hat, jacket, leftSleeve, rightSleeve, leftPants, rightPants);
    }

    @Override
    public List<SettingGroup> getSettingGroups() {
        return List.of(
            new SettingGroup("General", true, enabled),
            new SettingGroup("Layer Thickness", true, 
                headThickness, bodyThickness, armsThickness, legsThickness),
            new SettingGroup("Layer Visibility", false,
                hat, jacket, leftSleeve, rightSleeve, leftPants, rightPants)
        );
    }

    public boolean isLayerEnabled(String layer) {
        return switch (layer) {
            case "hat" -> hat.get();
            case "jacket" -> jacket.get();
            case "leftSleeve" -> leftSleeve.get();
            case "rightSleeve" -> rightSleeve.get();
            case "leftPants" -> leftPants.get();
            case "rightPants" -> rightPants.get();
            default -> true;
        };
    }

    public static Set<Item> hideHeadLayers() {
        return HIDE_HEAD_LAYERS;
    }

    public static float getHeadThickness() {
        var m = getInstance();
        return m != null ? m.headThickness.get() : 0.4f;
    }

    public static float getBodyThickness() {
        var m = getInstance();
        return m != null ? m.bodyThickness.get() : 0.5f;
    }

    public static float getArmsThickness() {
        var m = getInstance();
        return m != null ? m.armsThickness.get() : 0.3f;
    }

    public static float getLegsThickness() {
        var m = getInstance();
        return m != null ? m.legsThickness.get() : 0.3f;
    }
}
