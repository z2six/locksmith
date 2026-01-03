// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/profile/LockRenderProfilesLoader.java
package org.z2six.locksmith.render.profile;

import com.google.gson.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.config.LockProfileConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads server-authoritative lock render profiles from locksmith_profiles.json.
 *
 * For CHEST profiles:
 * - JSON key "doubleNudgeX" is mapped into hingeNudgeLeft/hingeNudgeRight,
 *   so it rides along the existing S2C path without adding new fields.
 *
 * For DOOR profiles:
 * - JSON keys "hingeNudgeLeft"/"hingeNudgeRight" are used as before.
 */
public final class LockRenderProfilesLoader {

    private static final Logger LOG = Constants.LOG;
    private static final Gson GSON = new GsonBuilder().create();

    private LockRenderProfilesLoader() {
    }

    public static Map<ResourceLocation, LockRenderProfile> loadFromDisk() {
        Map<ResourceLocation, LockRenderProfile> out = new Object2ObjectOpenHashMap<>();
        Path path = LockProfileConfig.getConfigPath();

        try {
            if (!Files.exists(path)) {
                LOG.info("[Locksmith][LockRenderProfilesLoader] No {} found; using empty server profile map.",
                        path.toAbsolutePath());
                return out;
            }

            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonElement rootEl = JsonParser.parseReader(reader);
                if (!rootEl.isJsonObject()) {
                    LOG.error("[Locksmith][LockRenderProfilesLoader] Root JSON is not an object in {}.",
                            path.toAbsolutePath());
                    return out;
                }

                JsonObject rootObj = rootEl.getAsJsonObject();
                JsonElement profilesEl = rootObj.get("profiles");
                if (profilesEl == null || !profilesEl.isJsonArray()) {
                    LOG.warn("[Locksmith][LockRenderProfilesLoader] No 'profiles' array in {}. Using empty map.",
                            path.toAbsolutePath());
                    return out;
                }

                JsonArray profilesArr = profilesEl.getAsJsonArray();
                int totalProfiles = 0;
                int totalEntries = 0;
                int invalidBlocks = 0;

                for (JsonElement el : profilesArr) {
                    if (!el.isJsonObject()) {
                        continue;
                    }
                    JsonObject profObj = el.getAsJsonObject();
                    totalProfiles++;

                    String idStr = getStringOrDefault(profObj, "id", "");
                    String typeStr = getStringOrDefault(profObj, "type", "door");
                    LockTargetType targetType = LockTargetType.fromString(typeStr);

                    JsonObject renderObj = profObj.has("render") && profObj.get("render").isJsonObject()
                            ? profObj.getAsJsonObject("render")
                            : new JsonObject();

                    // Defaults match the initial config file Locksmith writes out.
                    final boolean isChest = (targetType == LockTargetType.CHEST);
                    final double defOffsetX = isChest ? -0.025D : -0.05D;
                    final double defOffsetY = isChest ? 0.05D : 0.5D;
                    final double defOffsetZ = isChest ? 0.45D : -0.5D;
                    final float defRotX = 0.0F;
                    final float defRotY = isChest ? 180.0F : 0.0F;
                    final float defRotZ = 0.0F;
                    final float defScale = 0.75F;

                    double offsetX = getDoubleOrDefault(renderObj, "offsetX", defOffsetX);
                    double offsetY = getDoubleOrDefault(renderObj, "offsetY", defOffsetY);
                    double offsetZ = getDoubleOrDefault(renderObj, "offsetZ", defOffsetZ);

                    float rotX = (float) getDoubleOrDefault(renderObj, "rotX", defRotX);
                    float rotY = (float) getDoubleOrDefault(renderObj, "rotY", defRotY);
                    float rotZ = (float) getDoubleOrDefault(renderObj, "rotZ", defRotZ);

                    float scale = (float) getDoubleOrDefault(renderObj, "scale", defScale);

                    double hingeLeft;
                    double hingeRight;

                    if (isChest) {
                        // CHEST: read doubleNudgeX and shove it into hingeLeft/Right for S2C transport.
                        double defDoubleNudge = 0.25D;
                        double chestDoubleNudge = getDoubleOrDefault(renderObj, "doubleNudgeX", defDoubleNudge);
                        hingeLeft = chestDoubleNudge;
                        hingeRight = chestDoubleNudge;
                    } else {
                        // DOOR: keep existing semantics.
                        double defHingeLeft = 0.18D;
                        double defHingeRight = 0.325D;
                        hingeLeft = getDoubleOrDefault(renderObj, "hingeNudgeLeft", defHingeLeft);
                        hingeRight = getDoubleOrDefault(renderObj, "hingeNudgeRight", defHingeRight);
                    }

                    JsonElement blocksEl = profObj.get("blocks");
                    if (blocksEl == null || !blocksEl.isJsonArray()) {
                        LOG.warn("[Locksmith][LockRenderProfilesLoader] Profile '{}' has no 'blocks' array; skipping.",
                                idStr);
                        continue;
                    }

                    JsonArray blocksArr = blocksEl.getAsJsonArray();
                    for (JsonElement blkEl : blocksArr) {
                        if (!blkEl.isJsonPrimitive() || !blkEl.getAsJsonPrimitive().isString()) {
                            continue;
                        }
                        String blkStr = blkEl.getAsString();
                        if (blkStr == null || blkStr.isBlank()) {
                            continue;
                        }

                        ResourceLocation key = ResourceLocation.tryParse(blkStr);
                        if (key == null) {
                            invalidBlocks++;
                            LOG.warn("[Locksmith][LockRenderProfilesLoader] Invalid block id '{}' in profile '{}'; skipping.",
                                    blkStr, idStr);
                            continue;
                        }

                        LockRenderProfile prof = new LockRenderProfile(
                                targetType,
                                key,
                                offsetX, offsetY, offsetZ,
                                rotX, rotY, rotZ,
                                scale,
                                hingeLeft, hingeRight
                        );

                        if (!prof.isValid()) {
                            LOG.warn("[Locksmith][LockRenderProfilesLoader] Skipping invalid profile entry for block '{}'.", key);
                            continue;
                        }

                        out.put(key, prof);
                        totalEntries++;
                    }
                }

                LOG.info(
                        "[Locksmith][LockRenderProfilesLoader] Loaded {} JSON profile(s), {} block entry/entries ({} invalid block ids) from {}.",
                        totalProfiles, totalEntries, invalidBlocks, path.toAbsolutePath()
                );
            }
        } catch (IOException e) {
            LOG.error("[Locksmith][LockRenderProfilesLoader] IO error reading {} (non-fatal).", path.toAbsolutePath(), e);
        } catch (JsonParseException e) {
            LOG.error("[Locksmith][LockRenderProfilesLoader] JSON parse error in {} (non-fatal).", path.toAbsolutePath(), e);
        } catch (Throwable t) {
            LOG.error("[Locksmith][LockRenderProfilesLoader] Unexpected error reading {} (non-fatal).",
                    path.toAbsolutePath(), t);
        }

        return out;
    }

    private static String getStringOrDefault(JsonObject obj, String key, String def) {
        try {
            if (obj == null || !obj.has(key)) return def;
            JsonElement el = obj.get(key);
            if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) return def;
            String v = el.getAsString();
            return v == null ? def : v;
        } catch (Throwable t) {
            return def;
        }
    }

    private static double getDoubleOrDefault(JsonObject obj, String key, double def) {
        try {
            if (obj == null || !obj.has(key)) return def;
            JsonElement el = obj.get(key);
            if (!el.isJsonPrimitive()) return def;
            return el.getAsDouble();
        } catch (Throwable t) {
            return def;
        }
    }
}
