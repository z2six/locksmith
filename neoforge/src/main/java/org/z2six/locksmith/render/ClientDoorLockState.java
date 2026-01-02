// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/ClientDoorLockState.java
package org.z2six.locksmith.render;

import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Client-side cache of locked door positions -> required hash.
 * This is purely render/UX state; server remains authoritative.
 */
public final class ClientDoorLockState {

    private static final Logger LOG = Constants.LOG;

    private static final Long2ObjectOpenHashMap<String> LOCKED = new Long2ObjectOpenHashMap<>();

    private ClientDoorLockState() {
    }

    public static Long2ObjectMap<String> getSnapshot() {
        try {
            return Long2ObjectMaps.unmodifiable(LOCKED);
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientDoorLockState] getSnapshot failed (non-fatal).", t);
            return Long2ObjectMaps.emptyMap();
        }
    }

    public static boolean isLocked(long posLong) {
        try {
            return LOCKED.containsKey(posLong);
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientDoorLockState] isLocked failed (non-fatal).", t);
            return false;
        }
    }

    public static String getRequiredHash(long posLong) {
        try {
            String v = LOCKED.get(posLong);
            return v == null ? "" : v;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientDoorLockState] getRequiredHash failed (non-fatal).", t);
            return "";
        }
    }

    public static void setAll(Long2ObjectMap<String> map) {
        try {
            LOCKED.clear();
            if (map != null && !map.isEmpty()) {
                LOCKED.putAll(map);
            }
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientDoorLockState] setAll failed (non-fatal).", t);
        }
    }

    public static void put(long posLong, String hash) {
        try {
            if (hash == null || hash.isBlank()) return;
            LOCKED.put(posLong, hash);
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientDoorLockState] put failed (non-fatal).", t);
        }
    }

    public static void remove(long posLong) {
        try {
            LOCKED.remove(posLong);
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientDoorLockState] remove failed (non-fatal).", t);
        }
    }
}
