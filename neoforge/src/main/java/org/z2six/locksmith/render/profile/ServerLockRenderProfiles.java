// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/profile/ServerLockRenderProfiles.java
package org.z2six.locksmith.render.profile;

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

    private ServerLockRenderProfiles() {
    }

    /**
     * Returns a map suitable for network sync.
     * Called on player login (and whenever else you want to resync).
     */
    public static Map<ResourceLocation, LockRenderProfile> getProfilesForNetwork() {
        try {
            return LockableBlockProfileService.getProfilesForNetwork();
        } catch (Throwable t) {
            LOG.error("[Locksmith][ServerLockRenderProfiles] getProfilesForNetwork failed (non-fatal).", t);
            return Collections.emptyMap();
        }
    }

    /**
     * Forces a disk reload (used at startup or if you add a /reload hook later).
     */
    public static void reloadFromDisk() {
        LockableBlockProfileService.reloadFromDisk();
    }

    /**
     * Optional accessor if you ever want to query server-side placement logic.
     */
    public static Map<ResourceLocation, LockRenderProfile> getCachedProfiles() {
        try {
            return LockableBlockProfileService.getCachedProfiles();
        } catch (Throwable t) {
            LOG.error("[Locksmith][ServerLockRenderProfiles] getCachedProfiles failed (non-fatal).", t);
            return Collections.emptyMap();
        }
    }
}
