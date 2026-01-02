// MainFile: neoforge/src/main/java/org/z2six/locksmith/client/ClientLifecycleEvents.java
package org.z2six.locksmith.client;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.profile.ClientLockRenderProfiles;

/**
 * Client-only lifecycle hooks.
 * We clear cached server-synced visual config when disconnecting,
 * so singleplayer -> title -> join another server doesn't carry stale profiles.
 */
public final class ClientLifecycleEvents {

    private static final Logger LOG = Constants.LOG;

    private ClientLifecycleEvents() {
    }

    public static void onClientLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        try {
            ClientLockRenderProfiles.clear();
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientLifecycleEvents] onClientLoggedOut failed (non-fatal).", t);
        }
    }
}
