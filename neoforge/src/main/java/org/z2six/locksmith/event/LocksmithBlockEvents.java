// MainFile: neoforge/src/main/java/org/z2six/locksmith/event/LocksmithBlockEvents.java
package org.z2six.locksmith.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.lock.DoorLockManager;
import org.z2six.locksmith.network.RemoveDoorLockPayload;
import org.z2six.locksmith.world.DoorLockSavedData;

/**
 * World cleanup hooks.
 * Right now: remove persisted lock data when a door is broken, and sync removal to clients.
 */
public final class LocksmithBlockEvents {

    private static final Logger LOG = Constants.LOG;

    private LocksmithBlockEvents() {
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        try {
            if (event == null) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;

            BlockPos pos = event.getPos();
            if (pos == null) return;

            BlockState state = level.getBlockState(pos);
            // BreakEvent fires BEFORE the block is removed, so state should still be the door.
            if (!(state.getBlock() instanceof DoorBlock)) {
                return;
            }

            BlockPos doorPos = DoorLockManager.normalizeDoorPos(level, pos, state);

            DoorLockSavedData data = DoorLockSavedData.get(level);
            boolean removed = data.removeLock(doorPos);
            if (!removed) {
                return;
            }

            // Sync removal to all players in that dimension so the lock doesn't "stick" client-side.
            for (ServerPlayer other : level.players()) {
                PacketDistributor.sendToPlayer(other, new RemoveDoorLockPayload(doorPos.asLong()));
            }

            LOG.info("[Locksmith] Removed door lock data because door was broken at {}", doorPos);

        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithBlockEvents] onBlockBreak failed (non-fatal).", t);
        }
    }
}
