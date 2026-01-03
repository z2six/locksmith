// MainFile: neoforge/src/main/java/org/z2six/locksmith/client/ClientInit.java
package org.z2six.locksmith.client;

import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.DoorLockRenderer;

/**
 * Client-only initialization:
 * - Lock 3D rendering
 * - Client lifecycle cleanup
 *
 * NOTE:
 * HUD messages are now emitted via vanilla overlay message only (no render hook).
 */
public final class ClientInit {

    private static final Logger LOG = Constants.LOG;

    private ClientInit() {
    }

    public static void init() {
        LOG.info("[Locksmith][ClientInit] Starting client init...");

        try {
            NeoForge.EVENT_BUS.addListener(DoorLockRenderer::onRenderLevelStage);
            LOG.info("[Locksmith][ClientInit] Registered DoorLockRenderer (RenderLevelStageEvent).");
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientInit] Failed to register DoorLockRenderer (non-fatal).", t);
        }

        try {
            NeoForge.EVENT_BUS.addListener(ClientLifecycleEvents::onClientLoggedOut);
            LOG.info("[Locksmith][ClientInit] Registered ClientLifecycleEvents (ClientPlayerNetworkEvent.LoggingOut).");
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientInit] Failed to register ClientLifecycleEvents (non-fatal).", t);
        }

        LOG.info("[Locksmith][ClientInit] Client init complete.");
    }
}
