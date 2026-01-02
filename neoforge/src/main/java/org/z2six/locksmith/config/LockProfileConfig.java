// MainFile: neoforge/src/main/java/org/z2six/locksmith/config/LockProfileConfig.java
package org.z2six.locksmith.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-authoritative lock placement profiles.
 * - Lives in: <game root>/config/locksmith_profiles.json
 * - Currently only generates a default "vanilla_doors" profile.
 * - Later we can extend this to actually load/parse and feed the renderer.
 */
public final class LockProfileConfig {

    private static final Logger LOG = Constants.LOG;
    private static final String FILE_NAME = "locksmith_profiles.json";

    private LockProfileConfig() {
    }

    public static Path getConfigPath() {
        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            return configDir.resolve(FILE_NAME);
        } catch (Throwable t) {
            LOG.error("[Locksmith][LockProfileConfig] Failed to resolve config dir (non-fatal).", t);
            // Fallback: drop it in the working dir
            return Path.of(FILE_NAME);
        }
    }

    /**
     * If the JSON config does not exist yet, writes a default one containing
     * a single "vanilla_doors" profile with your current LockRenderTuning values.
     */
    public static void ensureDefaultFileExists() {
        try {
            Path path = getConfigPath();
            if (Files.exists(path)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][LockProfileConfig] Config file already exists at {}", path.toAbsolutePath());
                }
                return;
            }

            Path parent = path.getParent();
            if (parent != null) {
                try {
                    Files.createDirectories(parent);
                } catch (IOException ioe) {
                    LOG.error("[Locksmith][LockProfileConfig] Failed to create config directory {} (non-fatal).",
                            parent, ioe);
                }
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            JsonObject root = new JsonObject();
            JsonArray profiles = new JsonArray();

            // Default profile for vanilla doors using your current LockRenderTuning values.
            JsonObject doorProfile = new JsonObject();
            doorProfile.addProperty("id", "vanilla_doors");
            doorProfile.addProperty("type", "door");

            JsonArray blocks = new JsonArray();
            blocks.add("minecraft:oak_door");
            blocks.add("minecraft:spruce_door");
            blocks.add("minecraft:birch_door");
            blocks.add("minecraft:jungle_door");
            blocks.add("minecraft:acacia_door");
            blocks.add("minecraft:dark_oak_door");
            blocks.add("minecraft:mangrove_door");
            blocks.add("minecraft:cherry_door");
            blocks.add("minecraft:bamboo_door");
            blocks.add("minecraft:crimson_door");
            blocks.add("minecraft:warped_door");
            doorProfile.add("blocks", blocks);

            JsonObject render = new JsonObject();
            render.addProperty("offsetX", -0.05);
            render.addProperty("offsetY", 0.5);
            render.addProperty("offsetZ", -0.5);
            render.addProperty("rotX", 0.0);
            render.addProperty("rotY", 0.0);
            render.addProperty("rotZ", 0.0);
            render.addProperty("scale", 0.75);
            render.addProperty("hingeNudgeLeft", 0.18);
            render.addProperty("hingeNudgeRight", 0.325);
            doorProfile.add("render", render);

            profiles.add(doorProfile);
            root.add("profiles", profiles);
            root.addProperty(
                    "_comment",
                    "Locksmith: server-authoritative lock placement profiles.\n" +
                            "You can add more entries (e.g., chests or modded blocks) by following the same structure."
            );

            String json = gson.toJson(root);

            try {
                Files.writeString(path, json, StandardCharsets.UTF_8);
                LOG.info("[Locksmith][LockProfileConfig] Created default config at {}", path.toAbsolutePath());
            } catch (IOException ioe) {
                LOG.error("[Locksmith][LockProfileConfig] Failed to write default config to {} (non-fatal).",
                        path.toAbsolutePath(), ioe);
            }
        } catch (Throwable t) {
            LOG.error("[Locksmith][LockProfileConfig] ensureDefaultFileExists failed (non-fatal).", t);
        }
    }
}
