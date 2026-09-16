package com.crest.client.catstrokes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class CatStrokesConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("catstrokes.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static CatStrokesConfig instance;

    /** Render scale of the 612x354 cat canvas. */
    public float scale = 1.0f;

    /** How far the Live2D paw/eyes track the cursor (0=static). */
    public float mouseSensitivity = 2.0f;

    /** Draw the desk backdrop behind the cat/keyboard. */
    public boolean showBackdrop = true;

    public static CatStrokesConfig getInstance() {
        if (instance == null) instance = load();
        return instance;
    }

    public static void reload() {
        instance = load();
    }

    private static CatStrokesConfig load() {
        if (CONFIG_PATH.toFile().exists()) {
            try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
                return GSON.fromJson(reader, CatStrokesConfig.class);
            } catch (IOException e) {
                return new CatStrokesConfig();
            }
        }
        return new CatStrokesConfig();
    }

    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(this, writer);
        } catch (IOException ignored) {
        }
    }
}