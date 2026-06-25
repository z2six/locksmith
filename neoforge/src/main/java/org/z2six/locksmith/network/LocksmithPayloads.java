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
            registrar.playToServer(
                    LockChestPayload.TYPE,
                    LockChestPayload.STREAM_CODEC,
                    LockChestPayload::handle
            );
            registrar.playToServer(
                    LockGenericPayload.TYPE,
                    LockGenericPayload.STREAM_CODEC,
                    LockGenericPayload::handle
            );
            registrar.playToServer(
                    ToggleLockPayload.TYPE,
                    ToggleLockPayload.STREAM_CODEC,
                    ToggleLockPayload::handle
            );

            // NEW: C2S (Curios optional quick-equip)
            registrar.playToServer(
                    QuickEquipCuriosKeyPayload.TYPE,
                    QuickEquipCuriosKeyPayload.STREAM_CODEC,
                    QuickEquipCuriosKeyPayload::handle
            );
            registrar.playToServer(
                    SaveLockableBlocksPayload.TYPE,
                    SaveLockableBlocksPayload.STREAM_CODEC,
                    SaveLockableBlocksPayload::handle
            );
            registrar.playToServer(
                    ReloadLockableBlocksEditorPayload.TYPE,
                    ReloadLockableBlocksEditorPayload.STREAM_CODEC,
                    ReloadLockableBlocksEditorPayload::handle
            );

            // S2C (HUD feedback)
            registrar.playToClient(
                    HudMessagePayload.TYPE,
                    HudMessagePayload.STREAM_CODEC,
                    HudMessagePayload::handle
            );

            // S2C (doors)
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

            // S2C (chests)
            registrar.playToClient(
                    SyncChestLocksPayload.TYPE,
                    SyncChestLocksPayload.STREAM_CODEC,
                    SyncChestLocksPayload::handle
            );
            registrar.playToClient(
                    AddChestLockPayload.TYPE,
                    AddChestLockPayload.STREAM_CODEC,
                    AddChestLockPayload::handle
            );
            registrar.playToClient(
                    RemoveChestLockPayload.TYPE,
                    RemoveChestLockPayload.STREAM_CODEC,
                    RemoveChestLockPayload::handle
            );

            // S2C (generic single-block targets)
            registrar.playToClient(
                    SyncGenericLocksPayload.TYPE,
                    SyncGenericLocksPayload.STREAM_CODEC,
                    SyncGenericLocksPayload::handle
            );
            registrar.playToClient(
                    AddGenericLockPayload.TYPE,
                    AddGenericLockPayload.STREAM_CODEC,
                    AddGenericLockPayload::handle
            );
            registrar.playToClient(
                    RemoveGenericLockPayload.TYPE,
                    RemoveGenericLockPayload.STREAM_CODEC,
                    RemoveGenericLockPayload::handle
            );
            registrar.playToClient(
                    PulseGenericLockPayload.TYPE,
                    PulseGenericLockPayload.STREAM_CODEC,
                    PulseGenericLockPayload::handle
            );

            // Profiles
            registrar.playToClient(
                    SyncLockRenderProfilesPayload.TYPE,
                    SyncLockRenderProfilesPayload.STREAM_CODEC,
                    SyncLockRenderProfilesPayload::handle
            );
            registrar.playToClient(
                    OpenLockableBlocksEditorPayload.TYPE,
                    OpenLockableBlocksEditorPayload.STREAM_CODEC,
                    OpenLockableBlocksEditorPayload::handle
            );
            registrar.playToClient(
                    LockableBlocksEditorSaveResultPayload.TYPE,
                    LockableBlocksEditorSaveResultPayload.STREAM_CODEC,
                    LockableBlocksEditorSaveResultPayload::handle
            );

            LOG.info("[Locksmith] Registered payload handlers.");
        } catch (Throwable t) {
            LOG.error("[Locksmith] Failed to register payload handlers (this is bad).", t);
        }
    }
}
