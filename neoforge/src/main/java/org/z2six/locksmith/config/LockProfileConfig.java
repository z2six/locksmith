// neoforge/src/main/java/org/z2six/locksmith/config/LockProfileConfig.java
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
            return Path.of(FILE_NAME);
        }
    }

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

            // -------------------------
            // Default profile: vanilla doors
            // -------------------------
            JsonObject doorProfile = new JsonObject();
            doorProfile.addProperty("id", "vanilla_doors");
            doorProfile.addProperty("type", "door");

            JsonArray doorBlocks = new JsonArray();
            doorBlocks.add("minecraft:oak_door");
            doorBlocks.add("minecraft:spruce_door");
            doorBlocks.add("minecraft:birch_door");
            doorBlocks.add("minecraft:jungle_door");
            doorBlocks.add("minecraft:acacia_door");
            doorBlocks.add("minecraft:dark_oak_door");
            doorBlocks.add("minecraft:mangrove_door");
            doorBlocks.add("minecraft:cherry_door");
            doorBlocks.add("minecraft:bamboo_door");
            doorBlocks.add("minecraft:crimson_door");
            doorBlocks.add("minecraft:warped_door");
            doorProfile.add("blocks", doorBlocks);

            JsonObject doorRender = new JsonObject();
            doorRender.addProperty("offsetX", -0.05);
            doorRender.addProperty("offsetY", 0.5);
            doorRender.addProperty("offsetZ", -0.5);
            doorRender.addProperty("rotX", 0.0);
            doorRender.addProperty("rotY", 0.0);
            doorRender.addProperty("rotZ", 0.0);
            doorRender.addProperty("scale", 0.75);
            doorRender.addProperty("hingeNudgeLeft", 0.18);
            doorRender.addProperty("hingeNudgeRight", 0.325);
            doorProfile.add("render", doorRender);

            profiles.add(doorProfile);

            // -------------------------
            // Default profile: vanilla chests
            // -------------------------
            JsonObject chestProfile = new JsonObject();
            chestProfile.addProperty("id", "vanilla_chests");
            chestProfile.addProperty("type", "chest");

            JsonArray chestBlocks = new JsonArray();
            chestBlocks.add("minecraft:chest");
            chestBlocks.add("minecraft:trapped_chest");
            chestProfile.add("blocks", chestBlocks);

            JsonObject chestRender = new JsonObject();
            chestRender.addProperty("offsetX", -0.025);
            chestRender.addProperty("offsetY", 0.05);
            chestRender.addProperty("offsetZ", 0.45);
            chestRender.addProperty("rotX", 0.0);
            chestRender.addProperty("rotY", 180.0);
            chestRender.addProperty("rotZ", 0.0);
            chestRender.addProperty("scale", 0.75);
            chestProfile.add("render", chestRender);

            profiles.add(chestProfile);

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
