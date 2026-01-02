// MainFile: neoforge/src/main/java/org/z2six/locksmith/client/ClientInit.java
package org.z2six.locksmith.client;

import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.DoorLockRenderer;

/**
 * Client-only initializer. Called via reflection from main mod class to avoid server classloading issues.
 */
public final class ClientInit {

    private static final Logger LOG = Constants.LOG;

    private ClientInit() {
        // no-op
    }

    public static void init() {
        try {
            NeoForge.EVENT_BUS.addListener(DoorLockRenderer::onRenderLevelStage);
            LOG.info("[Locksmith][ClientInit] Registered DoorLockRenderer.");
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientInit] Failed to register DoorLockRenderer (non-fatal).", t);
        }
    }
}
