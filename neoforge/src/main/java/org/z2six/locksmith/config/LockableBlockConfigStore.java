// MainFile: neoforge/src/main/java/org/z2six/locksmith/config/LockableBlockConfigStore.java
package org.z2six.locksmith.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.profile.LockTargetType;
import org.z2six.locksmith.render.profile.LockTransform;
import org.z2six.locksmith.render.profile.LockableBlockDefaults;
import org.z2six.locksmith.render.profile.LockableBlockEntry;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LockableBlockConfigStore {
    private static final Logger LOG = Constants.LOG;
    private static final String FILE_NAME = "locksmith-lockable-blocks.toml";

    private LockableBlockConfigStore() {
    }

    public static Path getConfigPath() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        } catch (Throwable t) {
            LOG.error("[Locksmith][LockableBlockConfigStore] Failed to resolve config dir (non-fatal).", t);
            return Path.of(FILE_NAME);
        }
    }

    public static List<LockableBlockEntry> loadOrCreate() {
        Path path = getConfigPath();
        try {
            if (Files.exists(path)) {
                return loadToml(path);
            }

            List<LockableBlockEntry> imported = importLegacyJsonIfNeeded();
            List<LockableBlockEntry> entries = imported.isEmpty() ? LockableBlockDefaults.create() : imported;
            saveAtomically(entries);
            return entries;
        } catch (Throwable t) {
            LOG.error("[Locksmith][LockableBlockConfigStore] loadOrCreate failed (non-fatal, using defaults).", t);
            return LockableBlockDefaults.create();
        }
    }

    public static void saveAtomically(List<LockableBlockEntry> entries) throws IOException {
        Path path = getConfigPath();
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.writeString(tmp, toToml(entries), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailed) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static List<LockableBlockEntry> loadToml(Path path) throws IOException {
        ArrayList<LockableBlockEntry> out = new ArrayList<>();
        LinkedHashMap<String, String> current = null;

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (String raw : lines) {
            if (raw == null) continue;
            String line = stripInlineComment(raw).trim();
            if (line.isEmpty()) continue;

            if (line.equals("[[entries]]")) {
                addEntry(out, current);
                current = new LinkedHashMap<>();
                continue;
            }

            if (current == null) {
                continue;
            }

            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }

            String key = line.substring(0, eq).trim();
            String value = stripQuotes(line.substring(eq + 1).trim());
            current.put(key, value);
        }
        addEntry(out, current);

        return out.isEmpty() ? LockableBlockDefaults.create() : out;
    }

    public static List<LockableBlockEntry> importLegacyJsonIfNeeded() {
        ArrayList<LockableBlockEntry> out = new ArrayList<>();
        Path legacy = LockProfileConfig.getConfigPath();
        if (!Files.exists(legacy)) {
            return out;
        }

        try (BufferedReader reader = Files.newBufferedReader(legacy, StandardCharsets.UTF_8)) {
            JsonElement rootEl = JsonParser.parseReader(reader);
            if (!rootEl.isJsonObject()) {
                return out;
            }

            JsonElement profilesEl = rootEl.getAsJsonObject().get("profiles");
            if (profilesEl == null || !profilesEl.isJsonArray()) {
                return out;
            }

            for (JsonElement profileEl : profilesEl.getAsJsonArray()) {
                if (!profileEl.isJsonObject()) continue;
                JsonObject profile = profileEl.getAsJsonObject();
                LockTargetType type = LockTargetType.fromString(getString(profile, "type", "door"));
                JsonObject render = profile.has("render") && profile.get("render").isJsonObject()
                        ? profile.getAsJsonObject("render")
                        : new JsonObject();
                LockTransform defaults = defaultTransform(type);
                LockTransform transform = new LockTransform(
                        getDouble(render, "offsetX", defaults.offsetX()),
                        getDouble(render, "offsetY", defaults.offsetY()),
                        getDouble(render, "offsetZ", defaults.offsetZ()),
                        (float) getDouble(render, "rotX", defaults.rotX()),
                        (float) getDouble(render, "rotY", defaults.rotY()),
                        (float) getDouble(render, "rotZ", defaults.rotZ()),
                        (float) getDouble(render, "scale", defaults.scale()),
                        getDouble(render, "hingeNudgeLeft", defaults.hingeNudgeLeft()),
                        getDouble(render, "hingeNudgeRight", defaults.hingeNudgeRight()),
                        getDouble(render, "doubleNudgeX", defaults.doubleNudgeX())
                );

                JsonElement blocksEl = profile.get("blocks");
                if (blocksEl == null || !blocksEl.isJsonArray()) continue;
                JsonArray blocks = blocksEl.getAsJsonArray();
                for (JsonElement blockEl : blocks) {
                    if (!blockEl.isJsonPrimitive() || !blockEl.getAsJsonPrimitive().isString()) continue;
                    ResourceLocation id = ResourceLocation.tryParse(blockEl.getAsString());
                    if (id != null) {
                        out.add(new LockableBlockEntry(id, type, transform, false, true));
                    }
                }
            }
            LOG.info("[Locksmith][LockableBlockConfigStore] Imported {} legacy lockable block entries from {}.",
                    out.size(), legacy.toAbsolutePath());
        } catch (Throwable t) {
            LOG.error("[Locksmith][LockableBlockConfigStore] Legacy import failed from {} (non-fatal).",
                    legacy.toAbsolutePath(), t);
            out.clear();
        }
        return out;
    }

    private static void addEntry(List<LockableBlockEntry> out, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }

        ResourceLocation id = ResourceLocation.tryParse(values.getOrDefault("block", ""));
        if (id == null) {
            return;
        }

        LockTargetType type = LockTargetType.fromString(values.getOrDefault("type", "generic"));
        LockTransform defaults = defaultTransform(type);
        LockTransform transform = new LockTransform(
                getDouble(values, "offsetX", defaults.offsetX()),
                getDouble(values, "offsetY", defaults.offsetY()),
                getDouble(values, "offsetZ", defaults.offsetZ()),
                (float) getDouble(values, "rotX", defaults.rotX()),
                (float) getDouble(values, "rotY", defaults.rotY()),
                (float) getDouble(values, "rotZ", defaults.rotZ()),
                (float) getDouble(values, "scale", defaults.scale()),
                getDouble(values, "hingeNudgeLeft", defaults.hingeNudgeLeft()),
                getDouble(values, "hingeNudgeRight", defaults.hingeNudgeRight()),
                getDouble(values, "doubleNudgeX", defaults.doubleNudgeX())
        );

        boolean enabled = getBoolean(values, "enabled", true);
        boolean builtinDefault = getBoolean(values, "builtinDefault", false);
        out.add(new LockableBlockEntry(id, type, transform, builtinDefault, enabled));
    }

    private static String toToml(List<LockableBlockEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Locksmith lockable block editor config\n");
        sb.append("# This file is server-authoritative and is normally managed in-game with /locksmith editor.\n\n");

        if (entries != null) {
            for (LockableBlockEntry entry : entries) {
                if (entry == null || entry.blockId() == null) continue;
                LockTransform t = entry.transform().clamped();
                sb.append("[[entries]]\n");
                sb.append("block = \"").append(entry.blockId()).append("\"\n");
                sb.append("type = \"").append(entry.type().name().toLowerCase(Locale.ROOT)).append("\"\n");
                sb.append("enabled = ").append(entry.enabled()).append('\n');
                sb.append("builtinDefault = ").append(entry.builtinDefault()).append('\n');
                sb.append("offsetX = ").append(t.offsetX()).append('\n');
                sb.append("offsetY = ").append(t.offsetY()).append('\n');
                sb.append("offsetZ = ").append(t.offsetZ()).append('\n');
                sb.append("rotX = ").append(t.rotX()).append('\n');
                sb.append("rotY = ").append(t.rotY()).append('\n');
                sb.append("rotZ = ").append(t.rotZ()).append('\n');
                sb.append("scale = ").append(t.scale()).append('\n');
                sb.append("hingeNudgeLeft = ").append(t.hingeNudgeLeft()).append('\n');
                sb.append("hingeNudgeRight = ").append(t.hingeNudgeRight()).append('\n');
                sb.append("doubleNudgeX = ").append(t.doubleNudgeX()).append("\n\n");
            }
        }

        return sb.toString();
    }

    private static String stripInlineComment(String line) {
        int hash = line.indexOf('#');
        if (hash >= 0) {
            return line.substring(0, hash);
        }
        return line;
    }

    private static String stripQuotes(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static String getString(JsonObject obj, String key, String def) {
        try {
            JsonElement el = obj.get(key);
            if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                return el.getAsString();
            }
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        try {
            JsonElement el = obj.get(key);
            if (el != null && el.isJsonPrimitive()) {
                return el.getAsDouble();
            }
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static double getDouble(Map<String, String> values, String key, double def) {
        try {
            return Double.parseDouble(values.getOrDefault(key, Double.toString(def)));
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static boolean getBoolean(Map<String, String> values, String key, boolean def) {
        String raw = values.get(key);
        if (raw == null) return def;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.equals("true") || v.equals("yes") || v.equals("1")) return true;
        if (v.equals("false") || v.equals("no") || v.equals("0")) return false;
        return def;
    }

    private static LockTransform defaultTransform(LockTargetType type) {
        return switch (type == null ? LockTargetType.GENERIC : type) {
            case CHEST -> LockTransform.chestDefault();
            case GENERIC -> LockTransform.genericDefault();
            case DOOR -> LockTransform.doorDefault();
        };
    }
}
