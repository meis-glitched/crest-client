package com.crest.client.skinlayers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

import com.mojang.blaze3d.platform.NativeImage;
import com.crest.client.skinlayers.accessor.NativeImageAccessor;
import com.crest.client.skinlayers.accessor.PlayerSettings;
import com.crest.client.skinlayers.api.SkinLayersAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Avatar;

public final class CrestSkinUtil {

    private static final Cache<AbstractTexture, NativeImage> cache = CacheBuilder.newBuilder()
            .expireAfterAccess(60L, TimeUnit.SECONDS)
            .removalListener(new RemovalListener<AbstractTexture, NativeImage>() {
                @Override
                public void onRemoval(RemovalNotification<AbstractTexture, NativeImage> notification) {
                    try {
                        notification.getValue().close();
                    } catch (Exception ex) {
                        // ignore
                    }
                }
            }).build();

    // Track last used thickness values per player to detect changes
    private static final Map<Avatar, ThicknessCache> thicknessCache = new HashMap<>();

    private static class ThicknessCache {
        float head, body, arms, legs;
        
        ThicknessCache(float head, float body, float arms, float legs) {
            this.head = head; this.body = body; this.arms = arms; this.legs = legs;
        }
        
        boolean matches(float head, float body, float arms, float legs) {
            return this.head == head && this.body == body && this.arms == arms && this.legs == legs;
        }
    }

    private CrestSkinUtil() {
    }

    public static NativeImage getTexture(Identifier resourceLocation, boolean[] invalidated, boolean[] allocated) {
        if (resourceLocation == null) {
            return null;
        }
        if (allocated != null) {
            allocated[0] = false;
        }
        try {
            Optional<Resource> optionalRes = Minecraft.getInstance().getResourceManager().getResource(resourceLocation);
            if (optionalRes.isPresent()) {
                Resource resource = optionalRes.get();
                try (java.io.InputStream in = resource.open()) {
                    NativeImage skin = NativeImage.read(in);
                    if (allocated != null) {
                        allocated[0] = true;
                    }
                    return skin;
                }
            }
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(resourceLocation);
            if (texture == null) {
                return null;
            }
            NativeImage cachedImage = cache.getIfPresent(texture);
            if (cachedImage != null && (Object) cachedImage instanceof NativeImageAccessor ac
                    && ac.skinlayers$isAllocated()) {
                return cachedImage;
            } else {
                cache.invalidate(texture);
            }
            if (texture instanceof DynamicTexture) {
                try {
                    NativeImage img = ((DynamicTexture) texture).getPixels();
                    if (img != null && img.getWidth() > 0 && img.getHeight() > 0) {
                        NativeImage copy = new NativeImage(img.getWidth(), img.getHeight(), false);
                        copy.copyFrom(img);
                        if (allocated != null) {
                            allocated[0] = true;
                        }
                        return copy;
                    }
                } catch (Exception ex) {
                    // not backed by an image
                }
                return null;
            }
            if (invalidated != null) {
                invalidated[0] = true;
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    public static boolean setup3dLayers(Avatar abstractClientPlayerEntity, PlayerSettings settings,
            boolean thinArms, Identifier skinLocation) {
        if (skinLocation == null) {
            return false;
        }
        
        // Get current thickness values
        float headThickness = com.crest.client.core.SkinLayers3dModule.getHeadThickness();
        float bodyThickness = com.crest.client.core.SkinLayers3dModule.getBodyThickness();
        float armsThickness = com.crest.client.core.SkinLayers3dModule.getArmsThickness();
        float legsThickness = com.crest.client.core.SkinLayers3dModule.getLegsThickness();
        // Keep voxel depth proportional with the clamped scaling used by
        // CrestSkinConfig.refresh() so a maxed slider doesn't inflate the mesh.
        headThickness = clamp(headThickness, 0f, 0.4f);
        bodyThickness = clamp(bodyThickness, 0f, 0.5f);
        armsThickness = clamp(armsThickness, 0f, 0.3f);
        legsThickness = clamp(legsThickness, 0f, 0.3f);
        
        // Check if thickness values changed - if so, invalidate cache
        ThicknessCache cached = thicknessCache.get(abstractClientPlayerEntity);
        boolean thicknessChanged = cached == null || !cached.matches(headThickness, bodyThickness, armsThickness, legsThickness);
        
        if (thicknessChanged) {
            thicknessCache.put(abstractClientPlayerEntity, new ThicknessCache(headThickness, bodyThickness, armsThickness, legsThickness));
            settings.clearMeshes();
        }
        
        boolean[] invalidated = new boolean[1];
        if (!thicknessChanged && skinLocation.equals(settings.getCurrentSkin()) && thinArms == settings.hasThinArms()) {
            return settings.getHeadMesh() != null;
        }
        boolean[] allocated = new boolean[1];
        NativeImage skin = getTexture(skinLocation, invalidated, allocated);
        try {
            if (skin == null || skin.getWidth() != 64 || skin.getHeight() != 64) {
                settings.setCurrentSkin(skinLocation);
                settings.setThinArms(thinArms);
                settings.clearMeshes();
                return false;
            }
            
            // Calculate mesh depth based on thickness settings
            int legDepth = Math.round(4 + legsThickness * 4);
            int armDepth = Math.round(4 + armsThickness * 4);
            int bodyDepth = Math.round(4 + bodyThickness * 4);
            int headDepth = Math.round(8 + headThickness * 4);
            
            settings.setLeftLegMesh(SkinLayersAPI.meshHelper.create3DMesh(skin, 4, 12, legDepth, 0, 48, true, 0f));
            settings.setRightLegMesh(SkinLayersAPI.meshHelper.create3DMesh(skin, 4, 12, legDepth, 0, 32, true, 0f));
            if (thinArms) {
                settings.setLeftArmMesh(SkinLayersAPI.meshHelper.create3DMesh(skin, 3, 12, armDepth, 48, 48, true, -2f));
                settings.setRightArmMesh(SkinLayersAPI.meshHelper.create3DMesh(skin, 3, 12, armDepth, 40, 32, true, -2f));
            } else {
                settings.setLeftArmMesh(SkinLayersAPI.meshHelper.create3DMesh(skin, 4, 12, armDepth, 48, 48, true, -2));
                settings.setRightArmMesh(SkinLayersAPI.meshHelper.create3DMesh(skin, 4, 12, armDepth, 40, 32, true, -2));
            }
            settings.setTorsoMesh(SkinLayersAPI.meshHelper.create3DMesh(skin, 8, 12, bodyDepth, 16, 32, true, 0));
            settings.setHeadMesh(SkinLayersAPI.meshHelper.create3DMesh(skin, 8, 8, headDepth, 32, 0, false, 0.6f));
            settings.setCurrentSkin(skinLocation);
            settings.setThinArms(thinArms);
            return true;
        } finally {
            if (allocated[0] && skin != null) {
                skin.close();
            }
        }
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

}
