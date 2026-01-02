// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/profile/ClientLockRenderProfiles.java
package org.z2six.locksmith.render.profile;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

import java.util.Collections;
import java.util.Map;

/**
 * Client-side cache of render profiles received from server.
 * Purely visual; no gameplay authority.
 */
public final class ClientLockRenderProfiles {

    private static final Logger LOG = Constants.LOG;

    private static final Object2ObjectOpenHashMap<ResourceLocation, LockRenderProfile> PROFILES = new Object2ObjectOpenHashMap<>();

    private ClientLockRenderProfiles() {
    }

    public static void setAll(Map<ResourceLocation, LockRenderProfile> map) {
        try {
            PROFILES.clear();
            if (map != null && !map.isEmpty()) {
                PROFILES.putAll(map);
            }
            LOG.info("[Locksmith][Client] Synced lock render profiles. count={}", PROFILES.size());
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientLockRenderProfiles] setAll failed (non-fatal).", t);
        }
    }

    public static LockRenderProfile get(ResourceLocation targetId) {
        try {
            if (targetId == null) return null;
            return PROFILES.get(targetId);
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientLockRenderProfiles] get failed (non-fatal).", t);
            return null;
        }
    }

    public static int size() {
        try {
            return PROFILES.size();
        } catch (Throwable t) {
            return 0;
        }
    }

    public static Map<ResourceLocation, LockRenderProfile> snapshot() {
        try {
            return Collections.unmodifiableMap(new Object2ObjectOpenHashMap<>(PROFILES));
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientLockRenderProfiles] snapshot failed (non-fatal).", t);
            return Collections.emptyMap();
        }
    }

    public static void clear() {
        try {
            if (!PROFILES.isEmpty()) {
                LOG.info("[Locksmith][Client] Clearing lock render profiles cache (count={})", PROFILES.size());
            }
            PROFILES.clear();
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientLockRenderProfiles] clear failed (non-fatal).", t);
        }
    }
}
