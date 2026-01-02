// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/profile/LockRenderProfilesLoader.java
package org.z2six.locksmith.render.profile;

import com.google.gson.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.LockRenderTuning;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

/**
 * Loads profiles from a JSON file in the server config directory.
 *
 * Why JSON (not TOML ModConfig)?
 * - You explicitly want server admins to add arbitrary modded ids + transform data.
 * - JSON is easier to represent nested structures and future-proof (door/chest/etc).
 * - We can write a default file automatically.
 *
 * File path: config/locksmith_lock_profiles.json
 */
public final class LockRenderProfilesLoader {

    private static final Logger LOG = Constants.LOG;

    public static final String FILE_NAME = "locksmith_lock_profiles.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private LockRenderProfilesLoader() {
    }

    public static Path getConfigPath() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get();
            return dir.resolve(FILE_NAME);
        } catch (Throwable t) {
            LOG.error("[Locksmith][LockRenderProfilesLoader] Failed to resolve config dir; using current working dir (non-fatal).", t);
            return Paths.get(FILE_NAME);
        }
    }

    public static void ensureDefaultFileExists() {
        Path p = getConfigPath();
        try {
            if (Files.exists(p)) return;

            try {
                Files.createDirectories(p.getParent());
            } catch (Throwable ignored) {
            }

            String defaultJson = defaultJsonString();
            Files.writeString(p, defaultJson, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);

            LOG.info("[Locksmith] Wrote default lock render profiles config: {}", p.toAbsolutePath());
        } catch (FileAlreadyExistsException ignored) {
            // race-safe
        } catch (Throwable t) {
            LOG.error("[Locksmith] Failed to write default lock render profiles config (non-fatal). path={}", p.toAbsolutePath(), t);
        }
    }

    public static Map<ResourceLocation, LockRenderProfile> loadOrDefaults() {
        ensureDefaultFileExists();

        Path p = getConfigPath();
        try {
            if (!Files.exists(p)) {
                LOG.warn("[Locksmith] Render profiles config missing even after ensureDefaultFileExists. Using empty map.");
                return Map.of();
            }

            String json = Files.readString(p, StandardCharsets.UTF_8);
            if (json == null || json.isBlank()) {
                LOG.warn("[Locksmith] Render profiles config is blank. Using empty map. path={}", p.toAbsolutePath());
                return Map.of();
            }

            JsonElement rootEl;
            try {
                rootEl = JsonParser.parseString(json);
            } catch (Throwable parseFail) {
                LOG.error("[Locksmith] Render profiles config JSON parse failed. Using empty map. path={}", p.toAbsolutePath(), parseFail);
                return Map.of();
            }

            if (!rootEl.isJsonObject()) {
                LOG.warn("[Locksmith] Render profiles config root is not an object. Using empty map. path={}", p.toAbsolutePath());
                return Map.of();
            }

            JsonObject root = rootEl.getAsJsonObject();
            JsonArray arr = safeGetArray(root, "profiles");
            if (arr == null || arr.isEmpty()) {
                LOG.info("[Locksmith] Render profiles config has no profiles array or empty. path={}", p.toAbsolutePath());
                return Map.of();
            }

            Object2ObjectOpenHashMap<ResourceLocation, LockRenderProfile> out = new Object2ObjectOpenHashMap<>();

            for (int i = 0; i < arr.size(); i++) {
                JsonElement e = arr.get(i);
                if (!e.isJsonObject()) continue;
                LockRenderProfile prof = parseProfile(e.getAsJsonObject(), i);
                if (prof == null || !prof.isValid() || prof.target == null) continue;

                out.put(prof.target, prof);
            }

            LOG.info("[Locksmith] Loaded {} lock render profile(s) from {}", out.size(), p.toAbsolutePath());
            return out;

        } catch (NoSuchFileException nf) {
            LOG.warn("[Locksmith] Render profiles config not found. Using empty map. path={}", p.toAbsolutePath());
            return Map.of();
        } catch (IOException io) {
            LOG.error("[Locksmith] Render profiles config IO error. Using empty map. path={}", p.toAbsolutePath(), io);
            return Map.of();
        } catch (Throwable t) {
            LOG.error("[Locksmith] Render profiles config load failed unexpectedly. Using empty map. path={}", p.toAbsolutePath(), t);
            return Map.of();
        }
    }

    private static LockRenderProfile parseProfile(JsonObject obj, int index) {
        try {
            String typeStr = safeGetString(obj, "type", "door");
            LockTargetType type = LockTargetType.fromString(typeStr);

            String targetStr = safeGetString(obj, "target", "");
            ResourceLocation target = safeParseRL(targetStr);
            if (target == null) {
                LOG.warn("[Locksmith] Skipping render profile idx={} due to invalid target='{}'", index, targetStr);
                return null;
            }

            // offset array [x,y,z]
            double[] off = safeGetVec3Double(obj, "offset",
                    LockRenderTuning.OFFSET_X, LockRenderTuning.OFFSET_Y, LockRenderTuning.OFFSET_Z);

            // rot array [x,y,z]
            float[] rot = safeGetVec3Float(obj, "rot",
                    LockRenderTuning.ROT_X, LockRenderTuning.ROT_Y, LockRenderTuning.ROT_Z);

            float scale = safeGetFloat(obj, "scale", LockRenderTuning.SCALE);

            double hingeLeft = safeGetDouble(obj, "hingeNudgeLeft", LockRenderTuning.NUDGE_HINGE_LEFT);
            double hingeRight = safeGetDouble(obj, "hingeNudgeRight", LockRenderTuning.NUDGE_HINGE_RIGHT);

            return new LockRenderProfile(
                    type,
                    target,
                    off[0], off[1], off[2],
                    rot[0], rot[1], rot[2],
                    scale,
                    hingeLeft, hingeRight
            );
        } catch (Throwable t) {
            LOG.error("[Locksmith] Failed parsing render profile idx={} (non-fatal).", index, t);
            return null;
        }
    }

    private static String defaultJsonString() {
        // One example entry. Admins can copy/paste and add more profiles.
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);

        JsonArray arr = new JsonArray();

        JsonObject exampleDoor = new JsonObject();
        exampleDoor.addProperty("type", "door");
        exampleDoor.addProperty("target", "minecraft:oak_door");

        JsonArray off = new JsonArray();
        off.add(LockRenderTuning.OFFSET_X);
        off.add(LockRenderTuning.OFFSET_Y);
        off.add(LockRenderTuning.OFFSET_Z);
        exampleDoor.add("offset", off);

        JsonArray rot = new JsonArray();
        rot.add(LockRenderTuning.ROT_X);
        rot.add(LockRenderTuning.ROT_Y);
        rot.add(LockRenderTuning.ROT_Z);
        exampleDoor.add("rot", rot);

        exampleDoor.addProperty("scale", LockRenderTuning.SCALE);
        exampleDoor.addProperty("hingeNudgeLeft", LockRenderTuning.NUDGE_HINGE_LEFT);
        exampleDoor.addProperty("hingeNudgeRight", LockRenderTuning.NUDGE_HINGE_RIGHT);

        arr.add(exampleDoor);
        root.add("profiles", arr);

        return GSON.toJson(root);
    }

    private static JsonArray safeGetArray(JsonObject obj, String key) {
        try {
            if (obj == null || key == null) return null;
            JsonElement e = obj.get(key);
            if (e == null || !e.isJsonArray()) return null;
            return e.getAsJsonArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String safeGetString(JsonObject obj, String key, String def) {
        try {
            if (obj == null || key == null) return def;
            JsonElement e = obj.get(key);
            if (e == null || e.isJsonNull()) return def;
            if (!e.isJsonPrimitive()) return def;
            return e.getAsString();
        } catch (Throwable t) {
            return def;
        }
    }

    private static double safeGetDouble(JsonObject obj, String key, double def) {
        try {
            if (obj == null || key == null) return def;
            JsonElement e = obj.get(key);
            if (e == null || e.isJsonNull()) return def;
            if (!e.isJsonPrimitive()) return def;
            return e.getAsDouble();
        } catch (Throwable t) {
            return def;
        }
    }

    private static float safeGetFloat(JsonObject obj, String key, float def) {
        try {
            if (obj == null || key == null) return def;
            JsonElement e = obj.get(key);
            if (e == null || e.isJsonNull()) return def;
            if (!e.isJsonPrimitive()) return def;
            return e.getAsFloat();
        } catch (Throwable t) {
            return def;
        }
    }

    private static double[] safeGetVec3Double(JsonObject obj, String key, double dx, double dy, double dz) {
        double[] out = new double[]{dx, dy, dz};
        try {
            JsonElement e = obj.get(key);
            if (e == null || !e.isJsonArray()) return out;
            JsonArray a = e.getAsJsonArray();
            if (a.size() >= 1) out[0] = a.get(0).getAsDouble();
            if (a.size() >= 2) out[1] = a.get(1).getAsDouble();
            if (a.size() >= 3) out[2] = a.get(2).getAsDouble();
            return out;
        } catch (Throwable t) {
            return out;
        }
    }

    private static float[] safeGetVec3Float(JsonObject obj, String key, float dx, float dy, float dz) {
        float[] out = new float[]{dx, dy, dz};
        try {
            JsonElement e = obj.get(key);
            if (e == null || !e.isJsonArray()) return out;
            JsonArray a = e.getAsJsonArray();
            if (a.size() >= 1) out[0] = a.get(0).getAsFloat();
            if (a.size() >= 2) out[1] = a.get(1).getAsFloat();
            if (a.size() >= 3) out[2] = a.get(2).getAsFloat();
            return out;
        } catch (Throwable t) {
            return out;
        }
    }

    private static ResourceLocation safeParseRL(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            // 1.21+ has ResourceLocation.tryParse
            ResourceLocation rl = ResourceLocation.tryParse(s.trim());
            if (rl == null) return null;
            return rl;
        } catch (Throwable t) {
            return null;
        }
    }
}
