// MainFile: neoforge/src/main/java/org/z2six/locksmith/client/ClientInit.java
package org.z2six.locksmith.client;

import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.DoorLockRenderer;

public final class ClientInit {

    private static final Logger LOG = Constants.LOG;

    private ClientInit() {
    }

    public static void init() {
        try {
            NeoForge.EVENT_BUS.addListener(DoorLockRenderer::onRenderLevelStage);
            LOG.info("[Locksmith][ClientInit] Registered DoorLockRenderer.");
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientInit] Failed to register DoorLockRenderer (non-fatal).", t);
        }

        try {
            NeoForge.EVENT_BUS.addListener(ClientLifecycleEvents::onClientLoggedOut);
            LOG.info("[Locksmith][ClientInit] Registered ClientLifecycleEvents.");
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientInit] Failed to register ClientLifecycleEvents (non-fatal).", t);
        }

        try {
            NeoForge.EVENT_BUS.addListener(ClientHudMessages::onRenderGui);
            NeoForge.EVENT_BUS.addListener(ClientHudMessages::onClientTick);
            LOG.info("[Locksmith][ClientInit] Registered ClientHudMessages HUD overlay + tick.");
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientInit] Failed to register ClientHudMessages (non-fatal).", t);
        }
    }
}
