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

    // Cached copy; we always overwrite it when reloading.
    private static Map<ResourceLocation, LockRenderProfile> CACHED = Collections.emptyMap();

    private ServerLockRenderProfiles() {
    }

    /**
     * Load from disk and return a map suitable for network sync.
     * Called on player login (and whenever else you want to resync).
     */
    public static Map<ResourceLocation, LockRenderProfile> getProfilesForNetwork() {
        try {
            Map<ResourceLocation, LockRenderProfile> loaded = LockRenderProfilesLoader.loadFromDisk();
            if (loaded == null) {
                loaded = Collections.emptyMap();
            }

            // Store a defensive copy for potential future use.
            CACHED = new Object2ObjectOpenHashMap<>(loaded);

            if (LOG.isDebugEnabled()) {
                LOG.debug("[Locksmith][ServerLockRenderProfiles] Loaded {} profile entries for network.", loaded.size());
            }

            return loaded;
        } catch (Throwable t) {
            LOG.error("[Locksmith][ServerLockRenderProfiles] getProfilesForNetwork failed (non-fatal). Returning last cached map.", t);
            return CACHED != null ? CACHED : Collections.emptyMap();
        }
    }

    /**
     * Optional accessor if you ever want to query server-side placement logic.
     */
    public static Map<ResourceLocation, LockRenderProfile> getCachedProfiles() {
        return CACHED != null ? Collections.unmodifiableMap(CACHED) : Collections.emptyMap();
    }
}
