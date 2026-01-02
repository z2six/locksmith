// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/profile/ClientLockRenderProfiles.java
package org.z2six.locksmith.render.profile;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

import java.util.Collections;
import java.util.Map;

/**
 * Client-side cache of lock render profiles received from the server.
 * This is what DoorLockRenderer queries.
 */
public final class ClientLockRenderProfiles {

    private static final Logger LOG = Constants.LOG;

    private static final Object2ObjectOpenHashMap<ResourceLocation, LockRenderProfile> MAP =
            new Object2ObjectOpenHashMap<>();

    private ClientLockRenderProfiles() {
    }

    public static void setAll(Map<ResourceLocation, LockRenderProfile> profiles) {
        try {
            MAP.clear();
            if (profiles != null && !profiles.isEmpty()) {
                MAP.putAll(profiles);
            }
            LOG.info("[Locksmith][ClientLockRenderProfiles] setAll: now holding {} entries.", MAP.size());
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientLockRenderProfiles] setAll failed (non-fatal).", t);
        }
    }

    public static LockRenderProfile get(ResourceLocation id) {
        try {
            if (id == null) return null;
            return MAP.get(id);
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientLockRenderProfiles] get failed (non-fatal). id={}", id, t);
            return null;
        }
    }

    public static int size() {
        try {
            return MAP.size();
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientLockRenderProfiles] size failed (non-fatal).", t);
            return 0;
        }
    }

    public static Map<ResourceLocation, LockRenderProfile> snapshot() {
        try {
            return Collections.unmodifiableMap(new Object2ObjectOpenHashMap<>(MAP));
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientLockRenderProfiles] snapshot failed (non-fatal).", t);
            return Collections.emptyMap();
        }
    }

    public static void clear() {
        try {
            MAP.clear();
            LOG.debug("[Locksmith][ClientLockRenderProfiles] Cleared client profile cache.");
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientLockRenderProfiles] clear failed (non-fatal).", t);
        }
    }
}
