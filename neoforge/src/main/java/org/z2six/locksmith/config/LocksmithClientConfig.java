// MainFile: neoforge/src/main/java/org/z2six/locksmith/config/LocksmithClientConfig.java
package org.z2six.locksmith.config;

import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Very small hand-rolled TOML-style config for client QoL:
 *
 * File: config/locksmith-client.toml
 *
 * Example:
 * ---------------------------------
 * # Locksmith client QoL config
 * [qol]
 * autoCloseLockedDoors = true
 * autoCloseTicks = 20
 * doubleDoorSyncEnabled = true
 *
 * hudMessagesEnabled = true
 * hudMessageTicks = 40
 * hudScale = 1.0
 * hudOffsetY = -35
 * ---------------------------------
 *
 * - autoCloseLockedDoors       : enable/disable auto-close for locked doors
 * - autoCloseTicks             : delay in ticks (20 = 1 second)
 *
 * - doubleDoorSyncEnabled      : if true, opening/closing a LOCKED door will also open/close its paired "double door"
 *                                (only up to 2 doors, and only if the neighbor door is ALSO locked by Locksmith).
 *
 * - hudMessagesEnabled         : enable/disable HUD feedback text
 * - hudMessageTicks            : how long to show HUD messages (GUI frames, ~20 = 1s)
 * - hudScale                   : HUD text scale (1.0 = normal)
 * - hudOffsetY                 : vertical offset from screen center (negative = up)
 *
 * We parse a tiny subset of TOML:
 * - '#' / '//' / ';' comments
 * - section headers [qol] (ignored)
 * - simple "key = value" assignments
 */
public final class LocksmithClientConfig {

    private static final Logger LOG = Constants.LOG;

    private static final String FILE_NAME = "locksmith-client.toml";

    // Auto-close
    private static final boolean DEFAULT_AUTO_CLOSE_ENABLED = true;
    private static final int DEFAULT_AUTO_CLOSE_TICKS = 20;
    private static final int MIN_TICKS = 1;
    private static final int MAX_TICKS = 20 * 60 * 60; // 1 real-time hour at 20 TPS

    // Double-door sync (open + close)
    private static final boolean DEFAULT_DOUBLE_DOOR_SYNC_ENABLED = true;

    // HUD messages
    private static final boolean DEFAULT_HUD_MESSAGES_ENABLED = true;
    private static final int DEFAULT_HUD_MESSAGE_TICKS = 40; // ~2 seconds at 20 FPS
    private static final float DEFAULT_HUD_SCALE = 1.0F;
    private static final int DEFAULT_HUD_OFFSET_Y = -35;     // a bit above crosshair

    private static volatile boolean autoCloseEnabled = DEFAULT_AUTO_CLOSE_ENABLED;
    private static volatile int autoCloseTicks = DEFAULT_AUTO_CLOSE_TICKS;

    private static volatile boolean doubleDoorSyncEnabled = DEFAULT_DOUBLE_DOOR_SYNC_ENABLED;

    private static volatile boolean hudMessagesEnabled = DEFAULT_HUD_MESSAGES_ENABLED;
    private static volatile int hudMessageTicks = DEFAULT_HUD_MESSAGE_TICKS;
    private static volatile float hudScale = DEFAULT_HUD_SCALE;
    private static volatile int hudOffsetY = DEFAULT_HUD_OFFSET_Y;

    private LocksmithClientConfig() {
    }

    public static Path getConfigPath() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get();
            return dir.resolve(FILE_NAME);
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientConfig] Failed to resolve config dir (non-fatal).", t);
            return Path.of(FILE_NAME);
        }
    }

    // ---------- Auto-close getters ----------

    public static boolean isAutoCloseEnabled() {
        return autoCloseEnabled;
    }

    public static int getAutoCloseTicks() {
        return autoCloseTicks;
    }

    // ---------- Double-door sync getter ----------

    public static boolean isDoubleDoorSyncEnabled() {
        return doubleDoorSyncEnabled;
    }

    // ---------- HUD message getters ----------

    public static boolean isHudMessagesEnabled() {
        return hudMessagesEnabled;
    }

    /**
     * How long to show HUD messages in GUI frames (RenderGuiEvent calls).
     */
    public static int getHudMessageTicks() {
        return hudMessageTicks;
    }

    public static float getHudScale() {
        return hudScale;
    }

    /**
     * Vertical offset from screen center in GUI pixels.
     * Negative = above center, positive = below.
     */
    public static int getHudOffsetY() {
        return hudOffsetY;
    }

    /**
     * Load config from disk, or create a default file if it doesn't exist.
     * Safe to call multiple times; last successful load wins.
     */
    public static void loadOrCreate() {
        try {
            Path path = getConfigPath();
            if (!Files.exists(path)) {
                createDefaultFile(path);

                autoCloseEnabled = DEFAULT_AUTO_CLOSE_ENABLED;
                autoCloseTicks = DEFAULT_AUTO_CLOSE_TICKS;
                doubleDoorSyncEnabled = DEFAULT_DOUBLE_DOOR_SYNC_ENABLED;

                hudMessagesEnabled = DEFAULT_HUD_MESSAGES_ENABLED;
                hudMessageTicks = DEFAULT_HUD_MESSAGE_TICKS;
                hudScale = DEFAULT_HUD_SCALE;
                hudOffsetY = DEFAULT_HUD_OFFSET_Y;

                LOG.info(
                        "[Locksmith][ClientConfig] Created default config at {} "
                                + "(autoCloseEnabled={}, autoCloseTicks={}, doubleDoorSyncEnabled={}, hudMessagesEnabled={}, hudMessageTicks={}, hudScale={}, hudOffsetY={})",
                        path.toAbsolutePath(),
                        autoCloseEnabled,
                        autoCloseTicks,
                        doubleDoorSyncEnabled,
                        hudMessagesEnabled,
                        hudMessageTicks,
                        hudScale,
                        hudOffsetY
                );
                return;
            }

            // Load existing file
            boolean enabled = DEFAULT_AUTO_CLOSE_ENABLED;
            int ticks = DEFAULT_AUTO_CLOSE_TICKS;
            boolean ddSync = DEFAULT_DOUBLE_DOOR_SYNC_ENABLED;

            boolean hudEnabled = DEFAULT_HUD_MESSAGES_ENABLED;
            int hudTicks = DEFAULT_HUD_MESSAGE_TICKS;
            float hudScaleLocal = DEFAULT_HUD_SCALE;
            int hudOffsetYLocal = DEFAULT_HUD_OFFSET_Y;

            List<String> lines;
            try {
                lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            } catch (IOException io) {
                LOG.error("[Locksmith][ClientConfig] Failed to read {} (non-fatal, using defaults).",
                        path.toAbsolutePath(), io);

                autoCloseEnabled = DEFAULT_AUTO_CLOSE_ENABLED;
                autoCloseTicks = DEFAULT_AUTO_CLOSE_TICKS;
                doubleDoorSyncEnabled = DEFAULT_DOUBLE_DOOR_SYNC_ENABLED;

                hudMessagesEnabled = DEFAULT_HUD_MESSAGES_ENABLED;
                hudMessageTicks = DEFAULT_HUD_MESSAGE_TICKS;
                hudScale = DEFAULT_HUD_SCALE;
                hudOffsetY = DEFAULT_HUD_OFFSET_Y;
                return;
            }

            for (String raw : lines) {
                if (raw == null) continue;
                String line = raw.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#") || line.startsWith("//") || line.startsWith(";")) continue;

                // Ignore section headers like [qol]
                if (line.startsWith("[") && line.endsWith("]")) {
                    continue;
                }

                int eq = line.indexOf('=');
                if (eq <= 0) continue;

                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();

                // Strip quotes if user adds them
                key = stripQuotes(key);
                value = stripQuotes(value);

                String normKey = key.toLowerCase();

                // Auto-close keys
                if (normKey.equals("autocloselockeddoors") || normKey.equals("auto_close_locked_doors")) {
                    Boolean parsed = parseBoolean(value);
                    if (parsed != null) {
                        enabled = parsed;
                    } else {
                        LOG.warn("[Locksmith][ClientConfig] Invalid boolean for {}: '{}'", key, value);
                    }
                } else if (normKey.equals("autocloseticks") || normKey.equals("auto_close_ticks")) {
                    Integer parsed = parseIntClamped(value, MIN_TICKS, MAX_TICKS);
                    if (parsed != null) {
                        ticks = parsed;
                    } else {
                        LOG.warn("[Locksmith][ClientConfig] Invalid int for {}: '{}'", key, value);
                    }
                }
                // Double door sync keys
                else if (normKey.equals("doubledoorsyncenabled")
                        || normKey.equals("double_door_sync_enabled")
                        || normKey.equals("doubledoorsync")
                        || normKey.equals("double_door_sync")) {
                    Boolean parsed = parseBoolean(value);
                    if (parsed != null) {
                        ddSync = parsed;
                    } else {
                        LOG.warn("[Locksmith][ClientConfig] Invalid boolean for {}: '{}'", key, value);
                    }
                }
                // HUD keys
                else if (normKey.equals("hudmessagesenabled") || normKey.equals("hud_messages_enabled")) {
                    Boolean parsed = parseBoolean(value);
                    if (parsed != null) {
                        hudEnabled = parsed;
                    } else {
                        LOG.warn("[Locksmith][ClientConfig] Invalid boolean for {}: '{}'", key, value);
                    }
                } else if (normKey.equals("hudmessageticks") || normKey.equals("hud_message_ticks")) {
                    Integer parsed = parseIntClamped(value, 1, 20 * 60 * 10); // up to ~10 minutes of HUD, arbitrary
                    if (parsed != null) {
                        hudTicks = parsed;
                    } else {
                        LOG.warn("[Locksmith][ClientConfig] Invalid int for {}: '{}'", key, value);
                    }
                } else if (normKey.equals("hudscale") || normKey.equals("hud_scale")) {
                    Float parsed = parseFloatClamped(value, 0.25F, 4.0F);
                    if (parsed != null) {
                        hudScaleLocal = parsed;
                    } else {
                        LOG.warn("[Locksmith][ClientConfig] Invalid float for {}: '{}'", key, value);
                    }
                } else if (normKey.equals("hudoffsety") || normKey.equals("hud_offset_y")) {
                    Integer parsed = parseIntClamped(value, -2000, 2000);
                    if (parsed != null) {
                        hudOffsetYLocal = parsed;
                    } else {
                        LOG.warn("[Locksmith][ClientConfig] Invalid int for {}: '{}'", key, value);
                    }
                }
            }

            autoCloseEnabled = enabled;
            autoCloseTicks = ticks;
            doubleDoorSyncEnabled = ddSync;

            hudMessagesEnabled = hudEnabled;
            hudMessageTicks = hudTicks;
            hudScale = hudScaleLocal;
            hudOffsetY = hudOffsetYLocal;

            LOG.info(
                    "[Locksmith][ClientConfig] Loaded config from {} "
                            + "(autoCloseEnabled={}, autoCloseTicks={}, doubleDoorSyncEnabled={}, hudMessagesEnabled={}, hudMessageTicks={}, hudScale={}, hudOffsetY={})",
                    path.toAbsolutePath(),
                    autoCloseEnabled,
                    autoCloseTicks,
                    doubleDoorSyncEnabled,
                    hudMessagesEnabled,
                    hudMessageTicks,
                    hudScale,
                    hudOffsetY
            );

        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientConfig] loadOrCreate failed (non-fatal, using defaults).", t);

            autoCloseEnabled = DEFAULT_AUTO_CLOSE_ENABLED;
            autoCloseTicks = DEFAULT_AUTO_CLOSE_TICKS;
            doubleDoorSyncEnabled = DEFAULT_DOUBLE_DOOR_SYNC_ENABLED;

            hudMessagesEnabled = DEFAULT_HUD_MESSAGES_ENABLED;
            hudMessageTicks = DEFAULT_HUD_MESSAGE_TICKS;
            hudScale = DEFAULT_HUD_SCALE;
            hudOffsetY = DEFAULT_HUD_OFFSET_Y;
        }
    }

    private static void createDefaultFile(Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                try {
                    Files.createDirectories(parent);
                } catch (IOException ioe) {
                    LOG.error("[Locksmith][ClientConfig] Failed to create config directory {} (non-fatal).",
                            parent, ioe);
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("# Locksmith client QoL config\n");
            sb.append("# Controls automatic closing of LOCKED doors.\n");
            sb.append("# autoCloseLockedDoors = true/false\n");
            sb.append("# autoCloseTicks       = ticks before auto-close (20 = 1 second)\n");
            sb.append("\n");
            sb.append("# Double door sync for LOCKED doors only.\n");
            sb.append("# If true, opening/closing a LOCKED door will also open/close its paired door\n");
            sb.append("# (only checks ONE neighbor and only syncs up to 2 doors total).\n");
            sb.append("# doubleDoorSyncEnabled = true/false\n");
            sb.append("\n");
            sb.append("# HUD feedback when locking / denied due to missing key.\n");
            sb.append("# hudMessagesEnabled   = true/false\n");
            sb.append("# hudMessageTicks      = how long to show HUD message (GUI frames, ~20 = 1 second)\n");
            sb.append("# hudScale             = text scale (1.0 = normal)\n");
            sb.append("# hudOffsetY           = vertical offset from screen center (negative = up)\n");
            sb.append("\n");
            sb.append("[qol]\n");
            sb.append("autoCloseLockedDoors = ").append(DEFAULT_AUTO_CLOSE_ENABLED).append("\n");
            sb.append("autoCloseTicks = ").append(DEFAULT_AUTO_CLOSE_TICKS).append("\n");
            sb.append("doubleDoorSyncEnabled = ").append(DEFAULT_DOUBLE_DOOR_SYNC_ENABLED).append("\n");
            sb.append("\n");
            sb.append("hudMessagesEnabled = ").append(DEFAULT_HUD_MESSAGES_ENABLED).append("\n");
            sb.append("hudMessageTicks = ").append(DEFAULT_HUD_MESSAGE_TICKS).append("\n");
            sb.append("hudScale = ").append(DEFAULT_HUD_SCALE).append("\n");
            sb.append("hudOffsetY = ").append(DEFAULT_HUD_OFFSET_Y).append("\n");

            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientConfig] Failed to write default config (non-fatal).", t);
        }
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() >= 2 &&
                ((s.startsWith("\"") && s.endsWith("\"")) ||
                        (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private static Boolean parseBoolean(String value) {
        String v = value.toLowerCase();
        if (v.equals("true") || v.equals("yes") || v.equals("on") || v.equals("1")) {
            return Boolean.TRUE;
        }
        if (v.equals("false") || v.equals("no") || v.equals("off") || v.equals("0")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static Integer parseIntClamped(String value, int min, int max) {
        try {
            int i = Integer.parseInt(value);
            if (i < min) i = min;
            if (i > max) i = max;
            return i;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Float parseFloatClamped(String value, float min, float max) {
        try {
            float f = Float.parseFloat(value);
            if (f < min) f = min;
            if (f > max) f = max;
            return f;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
