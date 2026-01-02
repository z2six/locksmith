// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/LocksmithPayloads.java
package org.z2six.locksmith.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

public final class LocksmithPayloads {

    private static final Logger LOG = Constants.LOG;

    private LocksmithPayloads() {
    }

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        try {
            final var registrar = event.registrar(Constants.MOD_ID).versioned("1");

            registrar.playToServer(
                    RegisterIronKeyPayload.TYPE,
                    RegisterIronKeyPayload.STREAM_CODEC,
                    RegisterIronKeyPayload::handle
            );
            registrar.playToServer(
                    LockDoorPayload.TYPE,
                    LockDoorPayload.STREAM_CODEC,
                    LockDoorPayload::handle
            );

            registrar.playToClient(
                    SyncDoorLocksPayload.TYPE,
                    SyncDoorLocksPayload.STREAM_CODEC,
                    SyncDoorLocksPayload::handle
            );
            registrar.playToClient(
                    AddDoorLockPayload.TYPE,
                    AddDoorLockPayload.STREAM_CODEC,
                    AddDoorLockPayload::handle
            );
            registrar.playToClient(
                    RemoveDoorLockPayload.TYPE,
                    RemoveDoorLockPayload.STREAM_CODEC,
                    RemoveDoorLockPayload::handle
            );

            // NEW: render profile sync (server-authoritative visual config)
            registrar.playToClient(
                    SyncLockRenderProfilesPayload.TYPE,
                    SyncLockRenderProfilesPayload.STREAM_CODEC,
                    SyncLockRenderProfilesPayload::handle
            );

            LOG.info("[Locksmith] Registered payload handlers.");
        } catch (Throwable t) {
            LOG.error("[Locksmith] Failed to register payload handlers (this is bad).", t);
        }
    }
}
