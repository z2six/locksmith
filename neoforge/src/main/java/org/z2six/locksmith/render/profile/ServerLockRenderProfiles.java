// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/profile/ServerLockRenderProfiles.java
package org.z2six.locksmith.render.profile;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

import java.util.Collections;
import java.util.Map;

/**
 * Server-side holder for lock render profiles.
 *
 * Backed by locksmith_profiles.json (via LockRenderProfilesLoader).
 * This is the authoritative source that we send to clients in SyncLockRenderProfilesPayload.
 */
public final class ServerLockRenderProfiles {

    private static final Logger LOG = Constants.LOG;

    private static volatile Map<ResourceLocation, LockRenderProfile> CACHED = Collections.emptyMap();
    private static volatile boolean LOADED = false;

    private ServerLockRenderProfiles() {
    }

    /**
     * Returns a map suitable for network sync.
     * Called on player login (and whenever else you want to resync).
     */
    public static Map<ResourceLocation, LockRenderProfile> getProfilesForNetwork() {
        try {
            ensureLoaded(false);
            return CACHED != null ? CACHED : Collections.emptyMap();
        } catch (Throwable t) {
            LOG.error("[Locksmith][ServerLockRenderProfiles] getProfilesForNetwork failed (non-fatal). Returning last cached map.", t);
            return CACHED != null ? CACHED : Collections.emptyMap();
        }
    }

    /**
     * Forces a disk reload (used at startup or if you add a /reload hook later).
     */
    public static void reloadFromDisk() {
        ensureLoaded(true);
    }

    /**
     * Optional accessor if you ever want to query server-side placement logic.
     */
    public static Map<ResourceLocation, LockRenderProfile> getCachedProfiles() {
        ensureLoaded(false);
        return CACHED != null ? Collections.unmodifiableMap(CACHED) : Collections.emptyMap();
    }

    private static void ensureLoaded(boolean force) {
        try {
            if (!force && LOADED) {
                return;
            }

            synchronized (ServerLockRenderProfiles.class) {
                // Double-check inside lock.
                if (!force && LOADED) {
                    return;
                }

                Map<ResourceLocation, LockRenderProfile> loaded = LockRenderProfilesLoader.loadFromDisk();
                if (loaded == null) loaded = Collections.emptyMap();

                CACHED = new Object2ObjectOpenHashMap<>(loaded);
                LOADED = true;

                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][ServerLockRenderProfiles] Loaded {} profile entries (force={}).",
                            loaded.size(), force);
                }
            }
        } catch (Throwable t) {
            // Don't flip LOADED on failure; keep existing cache.
            LOG.error("[Locksmith][ServerLockRenderProfiles] ensureLoaded failed (non-fatal). Using cached map.", t);
        }
    }
}
