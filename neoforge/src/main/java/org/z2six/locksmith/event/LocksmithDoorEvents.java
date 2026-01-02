// MainFile: neoforge/src/main/java/org/z2six/locksmith/event/LocksmithDoorEvents.java
package org.z2six.locksmith.event;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.lock.DoorLockManager;
import org.z2six.locksmith.network.AddDoorLockPayload;
import org.z2six.locksmith.network.SyncDoorLocksPayload;
import org.z2six.locksmith.registry.ModItems;
import org.z2six.locksmith.world.DoorLockSavedData;

import java.util.Map;

/**
 * Server-side gameplay events for door locking + access control.
 */
public final class LocksmithDoorEvents {

    private static final Logger LOG = Constants.LOG;

    private LocksmithDoorEvents() {
        // no-op
    }

    /**
     * Sync all door locks to player on login.
     */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            ServerLevel level = sp.serverLevel();

            DoorLockSavedData data = DoorLockSavedData.get(level);
            Map<Long, String> locks = data.snapshotLocks();

            LongArrayList list = new LongArrayList(locks.size());
            for (Long posLong : locks.keySet()) {
                list.add(posLong.longValue());
            }

            PacketDistributor.sendToPlayer(sp, new SyncDoorLocksPayload(list));
            LOG.info("[Locksmith] Sent SyncDoorLocksPayload to {} (count={})", sp.getName().getString(), list.size());
        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithDoorEvents] onPlayerLoggedIn failed (non-fatal).", t);
        }
    }

    /**
     * Right-click door:
     * - If holding registered iron key in MAIN HAND: lock door if not already locked.
     * - If door locked: only allow opening if player has matching key anywhere in inventory.
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) {
                return; // server-side only for authoritative logic
            }

            ServerLevel level = sp.serverLevel();
            BlockPos clickedPos = event.getPos();
            BlockState clickedState = level.getBlockState(clickedPos);

            if (!(clickedState.getBlock() instanceof DoorBlock)) {
                return;
            }

            BlockPos doorPos = DoorLockManager.normalizeDoorPos(level, clickedPos, clickedState);
            BlockState doorState = level.getBlockState(doorPos);
            if (!(doorState.getBlock() instanceof DoorBlock)) {
                // weird edge case; bail
                return;
            }

            DoorLockSavedData data = DoorLockSavedData.get(level);

            boolean isLocked = data.isLocked(doorPos);

            // If player is holding registered key in main hand, try to lock (only if currently unlocked)
            if (!isLocked && event.getHand() == InteractionHand.MAIN_HAND) {
                ItemStack held = sp.getItemInHand(InteractionHand.MAIN_HAND);
                if (held != null && !held.isEmpty() && held.is(ModItems.KEY_IRON.get())) {
                    // Only lock if registered
                    if (org.z2six.locksmith.item.IronKeyItem.isRegistered(held)) {
                        boolean added = DoorLockManager.tryLockDoorWithHeldKey(level, sp, doorPos, held);
                        if (added) {
                            // Sync to all players in this dimension
                            for (ServerPlayer other : level.players()) {
                                PacketDistributor.sendToPlayer(other, new AddDoorLockPayload(doorPos.asLong()));
                            }
                            LOG.info("[Locksmith] Door locked at {} (sync sent).", doorPos);
                            // We still allow interaction to proceed (door may open). If you want “lock without opening”, we can cancel here.
                            isLocked = true;
                        }
                    }
                }
            }

            // If locked, enforce access control
            if (isLocked) {
                String requiredHash = data.getHash(doorPos);
                if (requiredHash == null || requiredHash.isBlank()) {
                    // Shouldn't happen, but if it does, treat as unlocked.
                    LOG.warn("[Locksmith] Door at {} marked locked but has blank hash. Allowing interaction.", doorPos);
                    return;
                }

                boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(sp, requiredHash);
                if (!hasKey) {
                    // Block door opening
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);

                    if (level.getGameTime() % 20 == 0) {
                        LOG.debug("[Locksmith] Blocked door open at {} for player {} (no matching key).",
                                doorPos, sp.getName().getString());
                    }
                }
            }

        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithDoorEvents] onRightClickBlock failed (non-fatal).", t);
        }
    }
}
