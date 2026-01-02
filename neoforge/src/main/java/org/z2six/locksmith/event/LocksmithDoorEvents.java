// MainFile: neoforge/src/main/java/org/z2six/locksmith/event/LocksmithDoorEvents.java
package org.z2six.locksmith.event;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.lock.DoorLockManager;
import org.z2six.locksmith.network.AddDoorLockPayload;
import org.z2six.locksmith.network.SyncDoorLocksPayload;
import org.z2six.locksmith.render.ClientDoorLockState;
import org.z2six.locksmith.world.DoorLockSavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Door locking + access control + client sync.
 */
public final class LocksmithDoorEvents {

    private static final Logger LOG = Constants.LOG;

    // Simple spam throttle: playerUUID -> lastDeniedGameTime
    private static final Map<String, Long> DENY_THROTTLE = new HashMap<>();

    private LocksmithDoorEvents() {
        // no-op
    }

    /**
     * Server: sync all door locks to player on login.
     */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            ServerLevel level = sp.serverLevel();

            DoorLockSavedData data = DoorLockSavedData.get(level);
            Map<Long, String> snap = data.snapshotLocks();

            Long2ObjectOpenHashMap<String> map = new Long2ObjectOpenHashMap<>(snap.size());
            for (Map.Entry<Long, String> e : snap.entrySet()) {
                if (e.getValue() != null && !e.getValue().isBlank()) {
                    map.put(e.getKey().longValue(), e.getValue());
                }
            }

            PacketDistributor.sendToPlayer(sp, new SyncDoorLocksPayload(map));
            LOG.info("[Locksmith] Sent SyncDoorLocksPayload to {} (count={})", sp.getName().getString(), map.size());
        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithDoorEvents] onPlayerLoggedIn failed (non-fatal).", t);
        }
    }

    /**
     * Fires on both sides.
     * We implement:
     * - CLIENT: prevent prediction flicker if locked + no matching key.
     * - SERVER: authoritative lock placement + authoritative access control + message.
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
            Player player = event.getEntity();
            Level level = event.getLevel();
            if (player == null || level == null) return;

            BlockPos clickedPos = event.getPos();
            BlockState clickedState = level.getBlockState(clickedPos);

            if (!(clickedState.getBlock() instanceof DoorBlock)) {
                return;
            }

            BlockPos doorPos = DoorLockManager.normalizeDoorPos(level, clickedPos, clickedState);
            BlockState doorState = level.getBlockState(doorPos);
            if (!(doorState.getBlock() instanceof DoorBlock)) {
                return;
            }

            // ------------------------
            // CLIENT: stop prediction flicker
            // ------------------------
            if (level.isClientSide) {
                long key = doorPos.asLong();
                if (!ClientDoorLockState.isLocked(key)) {
                    return;
                }

                String requiredHash = ClientDoorLockState.getRequiredHash(key);
                if (requiredHash == null || requiredHash.isBlank()) {
                    // If somehow blank, don't interfere.
                    return;
                }

                boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(player, requiredHash);
                if (!hasKey) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);
                }
                return; // client side ends here
            }

            // ------------------------
            // SERVER: authoritative
            // ------------------------
            if (!(player instanceof ServerPlayer sp)) {
                return;
            }
            ServerLevel sLevel = (ServerLevel) level;

            DoorLockSavedData data = DoorLockSavedData.get(sLevel);
            boolean isLocked = data.isLocked(doorPos);

            // If not locked yet AND player is holding registered key in MAIN_HAND: lock it
            if (!isLocked && event.getHand() == InteractionHand.MAIN_HAND) {
                ItemStack held = sp.getItemInHand(InteractionHand.MAIN_HAND);
                if (held != null && !held.isEmpty() && held.getItem() instanceof IronKeyItem) {
                    if (IronKeyItem.isRegistered(held)) {
                        boolean added = DoorLockManager.tryLockDoorWithHeldKey(sLevel, sp, doorPos, held);
                        if (added) {
                            // Send delta sync with hash to all players in this dimension
                            String hash = data.getHash(doorPos);
                            for (ServerPlayer other : sLevel.players()) {
                                PacketDistributor.sendToPlayer(other, new AddDoorLockPayload(doorPos.asLong(), hash));
                            }

                            LOG.info("[Locksmith] Door locked at {} (sync sent).", doorPos);

                            // IMPORTANT FIX: do not open the door on the same click as locking
                            event.setCanceled(true);
                            event.setCancellationResult(InteractionResult.SUCCESS);
                            return;
                        }
                    }
                }
            }

            // Enforce access control if locked
            if (data.isLocked(doorPos)) {
                String requiredHash = data.getHash(doorPos);
                if (requiredHash == null || requiredHash.isBlank()) {
                    LOG.warn("[Locksmith] Door at {} marked locked but has blank hash. Allowing interaction.", doorPos);
                    return;
                }

                boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(sp, requiredHash);
                if (!hasKey) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);

                    sendDeniedMessageThrottled(sp, sLevel.getGameTime());

                    LOG.debug("[Locksmith] Blocked door open at {} for player {} (no matching key).",
                            doorPos, sp.getName().getString());
                }
            }

        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithDoorEvents] onRightClickBlock failed (non-fatal).", t);
        }
    }

    private static void sendDeniedMessageThrottled(ServerPlayer player, long gameTime) {
        try {
            if (player == null) return;

            String id = player.getStringUUID();
            long last = DENY_THROTTLE.getOrDefault(id, -9999L);

            // 20 ticks = 1 second
            if (gameTime - last < 20) return;

            DENY_THROTTLE.put(id, gameTime);

            // Actionbar message (only this player sees it)
            player.displayClientMessage(
                    Component.translatable("message.locksmith.door_locked_no_key")
                            .withStyle(ChatFormatting.RED),
                    true
            );
        } catch (Throwable t) {
            LOG.warn("[Locksmith] sendDeniedMessageThrottled failed (non-fatal).", t);
        }
    }
}
