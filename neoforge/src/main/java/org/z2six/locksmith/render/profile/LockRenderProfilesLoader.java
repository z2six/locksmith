// neoforge/src/main/java/org/z2six/locksmith/render/profile/LockRenderProfilesLoader.java
package org.z2six.locksmith.render.profile;

import com.google.gson.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.config.LockProfileConfig;
import org.z2six.locksmith.render.LockRenderTuning;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][LockRenderProfilesLoader] Config file {} does not exist; returning empty map.", path.toAbsolutePath());
                }
                return out;
            }

            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonElement rootEl = JsonParser.parseReader(reader);
                if (!rootEl.isJsonObject()) {
                    LOG.error("[Locksmith][LockRenderProfilesLoader] Root JSON is not an object in {}.", path.toAbsolutePath());
                    return out;
                }

                JsonObject rootObj = rootEl.getAsJsonObject();
                JsonElement profilesEl = rootObj.get("profiles");
                if (profilesEl == null || !profilesEl.isJsonArray()) {
                    LOG.warn("[Locksmith][LockRenderProfilesLoader] No 'profiles' array in {}. Using empty map.", path.toAbsolutePath());
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

                    // Type-specific defaults
                    double defOffsetX;
                    double defOffsetY;
                    double defOffsetZ;
                    float defRotX;
                    float defRotY;
                    float defRotZ;
                    float defScale;
                    double defHingeLeft;
                    double defHingeRight;

                    if (targetType == LockTargetType.CHEST) {
                        defOffsetX = LockRenderTuning.CHEST_OFFSET_X;
                        defOffsetY = LockRenderTuning.CHEST_OFFSET_Y;
                        defOffsetZ = LockRenderTuning.CHEST_OFFSET_Z;
                        defRotX = LockRenderTuning.CHEST_ROT_X;
                        defRotY = LockRenderTuning.CHEST_ROT_Y;
                        defRotZ = LockRenderTuning.CHEST_ROT_Z;
                        defScale = LockRenderTuning.CHEST_SCALE;
                        // hinge nudges are not used for chests
                        defHingeLeft = 0.0;
                        defHingeRight = 0.0;
                    } else {
                        defOffsetX = LockRenderTuning.OFFSET_X;
                        defOffsetY = LockRenderTuning.OFFSET_Y;
                        defOffsetZ = LockRenderTuning.OFFSET_Z;
                        defRotX = LockRenderTuning.ROT_X;
                        defRotY = LockRenderTuning.ROT_Y;
                        defRotZ = LockRenderTuning.ROT_Z;
                        defScale = LockRenderTuning.SCALE;
                        defHingeLeft = LockRenderTuning.NUDGE_HINGE_LEFT;
                        defHingeRight = LockRenderTuning.NUDGE_HINGE_RIGHT;
                    }

                    double offsetX = getDoubleOrDefault(renderObj, "offsetX", defOffsetX);
                    double offsetY = getDoubleOrDefault(renderObj, "offsetY", defOffsetY);
                    double offsetZ = getDoubleOrDefault(renderObj, "offsetZ", defOffsetZ);

                    float rotX = (float) getDoubleOrDefault(renderObj, "rotX", defRotX);
                    float rotY = (float) getDoubleOrDefault(renderObj, "rotY", defRotY);
                    float rotZ = (float) getDoubleOrDefault(renderObj, "rotZ", defRotZ);

                    float scale = (float) getDoubleOrDefault(renderObj, "scale", defScale);

                    double hingeLeft = getDoubleOrDefault(renderObj, "hingeNudgeLeft", defHingeLeft);
                    double hingeRight = getDoubleOrDefault(renderObj, "hingeNudgeRight", defHingeRight);

                    JsonElement blocksEl = profObj.get("blocks");
                    if (blocksEl == null || !blocksEl.isJsonArray()) {
                        LOG.warn("[Locksmith][LockRenderProfilesLoader] Profile '{}' has no 'blocks' array; skipping.", idStr);
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
                            LOG.warn("[Locksmith][LockRenderProfilesLoader] Invalid block id '{}' in profile '{}'; skipping.", blkStr, idStr);
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

                LOG.info("[Locksmith][LockRenderProfilesLoader] Loaded {} profile(s), {} block entry/entries ({} invalid block ids) from {}.",
                        totalProfiles, totalEntries, invalidBlocks, path.toAbsolutePath());
            }
        } catch (IOException e) {
            LOG.error("[Locksmith][LockRenderProfilesLoader] IO error reading {} (non-fatal).", path.toAbsolutePath(), e);
        } catch (JsonParseException e) {
            LOG.error("[Locksmith][LockRenderProfilesLoader] JSON parse error in {} (non-fatal).", path.toAbsolutePath(), e);
        } catch (Throwable t) {
            LOG.error("[Locksmith][LockRenderProfilesLoader] Unexpected error reading {} (non-fatal).", path.toAbsolutePath(), t);
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
