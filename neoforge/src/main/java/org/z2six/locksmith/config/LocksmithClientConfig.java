// MainFile: LocksmithClientConfig.java
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
 * ---------------------------------
 *
 * - autoCloseLockedDoors : enable/disable auto-close for locked doors
 * - autoCloseTicks       : delay in ticks (20 = 1 second)
 *
 * We parse a tiny subset of TOML:
 * - '#' / '//' / ';' comments
 * - section headers [qol] (ignored)
 * - simple "key = value" assignments
 */
public final class LocksmithClientConfig {

    private static final Logger LOG = Constants.LOG;

    private static final String FILE_NAME = "locksmith-client.toml";

    private static final boolean DEFAULT_AUTO_CLOSE_ENABLED = true;
    private static final int DEFAULT_AUTO_CLOSE_TICKS = 20;
    private static final int MIN_TICKS = 1;
    private static final int MAX_TICKS = 20 * 60 * 60; // 1 real-time hour at 20 TPS

    private static volatile boolean autoCloseEnabled = DEFAULT_AUTO_CLOSE_ENABLED;
    private static volatile int autoCloseTicks = DEFAULT_AUTO_CLOSE_TICKS;

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

    public static boolean isAutoCloseEnabled() {
        return autoCloseEnabled;
    }

    public static int getAutoCloseTicks() {
        return autoCloseTicks;
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
                LOG.info(
                        "[Locksmith][ClientConfig] Created default config at {} (enabled={}, ticks={})",
                        path.toAbsolutePath(),
                        autoCloseEnabled,
                        autoCloseTicks
                );
                return;
            }

            // Load existing file
            boolean enabled = DEFAULT_AUTO_CLOSE_ENABLED;
            int ticks = DEFAULT_AUTO_CLOSE_TICKS;

            List<String> lines;
            try {
                lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            } catch (IOException io) {
                LOG.error("[Locksmith][ClientConfig] Failed to read {} (non-fatal, using defaults).",
                        path.toAbsolutePath(), io);
                autoCloseEnabled = DEFAULT_AUTO_CLOSE_ENABLED;
                autoCloseTicks = DEFAULT_AUTO_CLOSE_TICKS;
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
            }

            autoCloseEnabled = enabled;
            autoCloseTicks = ticks;

            LOG.info(
                    "[Locksmith][ClientConfig] Loaded config from {} (enabled={}, ticks={})",
                    path.toAbsolutePath(),
                    autoCloseEnabled,
                    autoCloseTicks
            );

        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientConfig] loadOrCreate failed (non-fatal, using defaults).", t);
            autoCloseEnabled = DEFAULT_AUTO_CLOSE_ENABLED;
            autoCloseTicks = DEFAULT_AUTO_CLOSE_TICKS;
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
            sb.append("[qol]\n");
            sb.append("autoCloseLockedDoors = ").append(DEFAULT_AUTO_CLOSE_ENABLED).append("\n");
            sb.append("autoCloseTicks = ").append(DEFAULT_AUTO_CLOSE_TICKS).append("\n");

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
}
