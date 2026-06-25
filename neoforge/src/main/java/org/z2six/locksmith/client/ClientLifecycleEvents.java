// neoforge/src/main/java/org/z2six/locksmith/client/ClientLifecycleEvents.java
package org.z2six.locksmith.client;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.ClientChestLockState;
import org.z2six.locksmith.render.ClientChestOpenBlocker;
import org.z2six.locksmith.render.ClientGenericLockState;
import org.z2six.locksmith.render.profile.ClientLockRenderProfiles;

public final class ClientLifecycleEvents {

    private static final Logger LOG = Constants.LOG;

    private ClientLifecycleEvents() {
    }

    public static void onClientLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        try {
            ClientLockRenderProfiles.clear();
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientLifecycleEvents] onClientLoggedOut failed clearing render profiles (non-fatal).", t);
        }

        try {
            ClientChestLockState.clear();
            ClientChestOpenBlocker.clear();
            ClientGenericLockState.clear();
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientLifecycleEvents] onClientLoggedOut failed clearing lock client caches (non-fatal).", t);
        }
    }
}
