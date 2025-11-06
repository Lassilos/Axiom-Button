package org.lassilos.color.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Simple properties-based configuration for the Color mod.
 * Stores booleans controlling whether the buttons should be shown in singleplayer/multiplayer.
 */
public final class ColorConfig {
    private static final String FILE_NAME = "color-config.properties";
    private static final String KEY_SHOW_SINGLE = "show_in_singleplayer";
    private static final String KEY_SHOW_MULTI = "show_in_multiplayer";
    private static final String KEY_DEBUG_O_ENABLED = "enable_debug_key_o";

    private static boolean loaded = false;
    private static boolean showInSingleplayer = false; // default: false (preserve current behavior)
    private static boolean showInMultiplayer = true;  // default: true
    private static boolean debugKeyOEnabled = true;   // default: true (keep debug behaviour enabled)

    private ColorConfig() {}

    public static synchronized void load() {
        if (loaded) return;
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve(FILE_NAME);
        Properties p = new Properties();
        if (Files.exists(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                p.load(in);
            } catch (IOException e) {
                System.out.println("[color] Failed to read config file: " + e.getMessage());
            }
        }
        // parse properties with safe defaults
        showInSingleplayer = Boolean.parseBoolean(p.getProperty(KEY_SHOW_SINGLE, Boolean.toString(showInSingleplayer)));
        showInMultiplayer = Boolean.parseBoolean(p.getProperty(KEY_SHOW_MULTI, Boolean.toString(showInMultiplayer)));
        debugKeyOEnabled = Boolean.parseBoolean(p.getProperty(KEY_DEBUG_O_ENABLED, Boolean.toString(debugKeyOEnabled)));

        // if config file was missing, write defaults
        if (!Files.exists(configFile)) {
            save();
        }
        loaded = true;
        System.out.println("[color] Config loaded: showInSingleplayer=" + showInSingleplayer + " showInMultiplayer=" + showInMultiplayer);
    }

    public static synchronized void save() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            System.out.println("[color] Failed to create config directory: " + e.getMessage());
            return;
        }
        Path configFile = configDir.resolve(FILE_NAME);
        Properties p = new Properties();
        p.setProperty(KEY_SHOW_SINGLE, Boolean.toString(showInSingleplayer));
        p.setProperty(KEY_SHOW_MULTI, Boolean.toString(showInMultiplayer));
        p.setProperty(KEY_DEBUG_O_ENABLED, Boolean.toString(debugKeyOEnabled));
        try (OutputStream out = Files.newOutputStream(configFile)) {
            p.store(out, "Color mod configuration: set show_in_singleplayer, show_in_multiplayer, enable_debug_key_o to true/false");
        } catch (IOException e) {
            System.out.println("[color] Failed to write config file: " + e.getMessage());
        }
    }

    private static void ensureLoaded() {
        if (!loaded) load();
    }

    public static boolean shouldShowInSingleplayer() { ensureLoaded(); return showInSingleplayer; }
    public static boolean shouldShowInMultiplayer() { ensureLoaded(); return showInMultiplayer; }
    public static boolean isDebugKeyOEnabled() { ensureLoaded(); return debugKeyOEnabled; }

    public static synchronized void setShowInSingleplayer(boolean v) { showInSingleplayer = v; save(); }
    public static synchronized void setShowInMultiplayer(boolean v) { showInMultiplayer = v; save(); }
    public static synchronized void setDebugKeyOEnabled(boolean v) { debugKeyOEnabled = v; save(); }
}
