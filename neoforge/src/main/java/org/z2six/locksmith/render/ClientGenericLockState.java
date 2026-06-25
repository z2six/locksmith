// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/ClientGenericLockState.java
package org.z2six.locksmith.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

public final class ClientGenericLockState {
    private static final Logger LOG = Constants.LOG;
    private static final Long2ObjectOpenHashMap<String> LOCKED = new Long2ObjectOpenHashMap<>();

    private ClientGenericLockState() {
    }

    public static Long2ObjectMap<String> getSnapshot() {
        try {
            return Long2ObjectMaps.unmodifiable(LOCKED);
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientGenericLockState] getSnapshot failed (non-fatal).", t);
            return Long2ObjectMaps.emptyMap();
        }
    }

    public static boolean isLocked(long posLong) {
        return LOCKED.containsKey(posLong);
    }

    public static String getRequiredHash(long posLong) {
        String v = LOCKED.get(posLong);
        return v == null ? "" : v;
    }

    public static void setAll(Long2ObjectMap<String> map) {
        LOCKED.clear();
        if (map != null && !map.isEmpty()) {
            LOCKED.putAll(map);
        }
    }

    public static void put(long posLong, String hash) {
        if (hash != null && !hash.isBlank()) {
            LOCKED.put(posLong, hash);
        }
    }

    public static void remove(long posLong) {
        LOCKED.remove(posLong);
    }

    public static void clear() {
        LOCKED.clear();
    }
}
