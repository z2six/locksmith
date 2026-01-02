// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/LocksmithPayloads.java
package org.z2six.locksmith.network;

import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Registers all custom payloads (C2S + S2C).
 */
public final class LocksmithPayloads {

    private static final Logger LOG = Constants.LOG;

    private LocksmithPayloads() {
    }

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        try {
            final var registrar = event.registrar(Constants.MOD_ID).versioned("1");

            // C2S
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

            // S2C
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

            LOG.info("[Locksmith] Registered payload handlers.");
        } catch (Throwable t) {
            LOG.error("[Locksmith] Failed to register payload handlers (this is bad).", t);
        }
    }
}
