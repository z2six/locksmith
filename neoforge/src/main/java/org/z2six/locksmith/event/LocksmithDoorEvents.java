// MainFile: neoforge/src/main/java/org/z2six/locksmith/event/LocksmithDoorEvents.java
package org.z2six.locksmith.event;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.lock.DoorLockManager;
import org.z2six.locksmith.network.*;
import org.z2six.locksmith.render.ClientDoorLockState;
import org.z2six.locksmith.render.ClientDoorOpenBlocker;
import org.z2six.locksmith.world.DoorLockSavedData;

import java.util.HashMap;
import java.util.Map;

public final class LocksmithDoorEvents {

    private static final Logger LOG = Constants.LOG;

    private static final Map<String, Long> DENY_THROTTLE = new HashMap<>();

    private static final int CLEANUP_EVERY_TICKS = 200;
    private static final int CLEANUP_MAX_CHECK_PER_PASS = 512;

    // This is ONLY for client prediction suppression when placing a lock.
    private static final int CLIENT_BLOCK_OPEN_TICKS_AFTER_LOCK_CLICK = 6;

    // Server-side "slam shut" safety net you already saw working.
    private static final int FORCE_CLOSE_AFTER_LOCK_TICKS = 5;
    private static final int FORCE_CLOSE_MAX_PER_TICK = 256;

    private LocksmithDoorEvents() {
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            ServerLevel level = sp.serverLevel();

            DoorLockSavedData data = DoorLockSavedData.get(level);
            data.cleanupInvalidDoors(level, CLEANUP_MAX_CHECK_PER_PASS);

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

            // -----------------------
            // CLIENT-SIDE BEHAVIOR
            // -----------------------
            if (level.isClientSide) {
                long doorLong = doorPos.asLong();

                // (Optional) opportunistic cleanup
                ClientDoorOpenBlocker.cleanupExpired(level.getGameTime(), 64);

                // If NOT locked, and holding registered key -> EAT + block predicted open + send payload.
                if (!ClientDoorLockState.isLocked(doorLong)
                        && event.getHand() == InteractionHand.MAIN_HAND) {

                    ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
                    if (held != null && !held.isEmpty()
                            && held.getItem() instanceof IronKeyItem
                            && IronKeyItem.isRegistered(held)) {

                        // IMPORTANT: block the open prediction window BEFORE anything else.
                        ClientDoorOpenBlocker.blockOpenForTicks(doorLong, level.getGameTime(), CLIENT_BLOCK_OPEN_TICKS_AFTER_LOCK_CLICK);

                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        safeDenyVanillaUse(event);

                        try {
                            PacketDistributor.sendToServer(new LockDoorPayload(doorLong));
                            LOG.debug("[Locksmith][Client] Lock click ate interaction and sent LockDoorPayload pos={}", doorPos);
                        } catch (Throwable t) {
                            LOG.error("[Locksmith][Client] Failed to send LockDoorPayload (non-fatal).", t);
                        }
                        return;
                    }
                }

                // If locked, deny when no key (already-working path).
                if (ClientDoorLockState.isLocked(doorLong)) {
                    String requiredHash = ClientDoorLockState.getRequiredHash(doorLong);
                    if (requiredHash != null && !requiredHash.isBlank()) {
                        boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(player, requiredHash);
                        if (!hasKey) {
                            event.setCanceled(true);
                            event.setCancellationResult(InteractionResult.FAIL);
                            safeDenyVanillaUse(event);
                            LOG.debug("[Locksmith][Client] Denied locked door use at {} (no key).", doorPos);
                        }
                    }
                }

                return;
            }

            // -----------------------
            // SERVER-SIDE BEHAVIOR
            // -----------------------
            if (!(player instanceof ServerPlayer sp)) {
                return;
            }
            ServerLevel sLevel = (ServerLevel) level;

            DoorLockSavedData data = DoorLockSavedData.get(sLevel);

            if (!(sLevel.getBlockState(doorPos).getBlock() instanceof DoorBlock)) {
                boolean removed = data.removeLock(doorPos);
                if (removed) {
                    broadcastRemoveToLevelPlayers(sLevel, doorPos.asLong());
                }
                return;
            }

            boolean isLocked = data.isLocked(doorPos);

            if (!isLocked && event.getHand() == InteractionHand.MAIN_HAND) {
                ItemStack held = sp.getItemInHand(InteractionHand.MAIN_HAND);
                if (held != null && !held.isEmpty()
                        && held.getItem() instanceof IronKeyItem
                        && IronKeyItem.isRegistered(held)) {

                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    safeDenyVanillaUse(event);

                    boolean added = DoorLockManager.tryLockDoorWithHeldKey(sLevel, sp, doorPos, held);

                    DoorLockManager.forceCloseDoor(sLevel, doorPos);
                    DoorLockManager.requestForceClose(sLevel, doorPos, FORCE_CLOSE_AFTER_LOCK_TICKS);

                    if (added) {
                        String hash = data.getHash(doorPos);
                        for (ServerPlayer other : sLevel.players()) {
                            PacketDistributor.sendToPlayer(other, new AddDoorLockPayload(doorPos.asLong(), hash));
                        }
                        LOG.info("[Locksmith] Door locked at {} (sync sent, interaction eaten, forced-close queued).", doorPos);
                    } else {
                        LOG.debug("[Locksmith] Lock attempt at {} ate interaction but did not add lock (already locked or invalid key).", doorPos);
                    }

                    return;
                }
            }

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
                    safeDenyVanillaUse(event);

                    sendDeniedMessageThrottled(sp, sLevel.getGameTime());

                    LOG.debug("[Locksmith] Blocked door open at {} for player {} (no matching key).",
                            doorPos, sp.getName().getString());
                }
            }

        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithDoorEvents] onRightClickBlock failed (non-fatal).", t);
        }
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        try {
            if (event == null) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;

            BlockPos pos = event.getPos();
            if (pos == null) return;

            BlockState st = level.getBlockState(pos);
            if (!(st.getBlock() instanceof DoorBlock)) {
                return;
            }

            BlockPos doorPos = DoorLockManager.normalizeDoorPos(level, pos, st);

            DoorLockSavedData data = DoorLockSavedData.get(level);
            boolean removed = data.removeLock(doorPos);
            if (removed) {
                broadcastRemoveToLevelPlayers(level, doorPos.asLong());
                LOG.info("[Locksmith] Removed lock due to player break at doorPos={}", doorPos);
            }
        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithDoorEvents] onBlockBreak failed (non-fatal).", t);
        }
    }

    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        try {
            if (event == null) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;

            if (event.getAffectedBlocks() == null || event.getAffectedBlocks().isEmpty()) return;

            DoorLockSavedData data = DoorLockSavedData.get(level);

            int removed = 0;
            for (BlockPos pos : event.getAffectedBlocks()) {
                if (pos == null) continue;

                BlockState st = level.getBlockState(pos);
                if (!(st.getBlock() instanceof DoorBlock)) continue;

                BlockPos doorPos = DoorLockManager.normalizeDoorPos(level, pos, st);
                boolean did = data.removeLock(doorPos);
                if (did) {
                    broadcastRemoveToLevelPlayers(level, doorPos.asLong());
                    removed++;
                }
            }

            if (removed > 0) {
                LOG.info("[Locksmith] Removed {} lock(s) due to explosion detonate.", removed);
            }
        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithDoorEvents] onExplosionDetonate failed (non-fatal).", t);
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        try {
            if (event == null) return;

            var server = event.getServer();
            if (server == null) return;

            for (ServerLevel level : server.getAllLevels()) {
                DoorLockManager.tickForceClose(level, FORCE_CLOSE_MAX_PER_TICK);
            }

            long gameTime = server.overworld().getGameTime();
            if (gameTime % CLEANUP_EVERY_TICKS != 0) return;

            for (ServerLevel level : server.getAllLevels()) {
                DoorLockSavedData data = DoorLockSavedData.get(level);
                int removedCount = data.cleanupInvalidDoors(level, CLEANUP_MAX_CHECK_PER_PASS);
                if (removedCount > 0) {
                    Map<Long, String> snap = data.snapshotLocks();
                    Long2ObjectOpenHashMap<String> map = new Long2ObjectOpenHashMap<>(snap.size());
                    for (Map.Entry<Long, String> e : snap.entrySet()) {
                        if (e.getValue() != null && !e.getValue().isBlank()) {
                            map.put(e.getKey().longValue(), e.getValue());
                        }
                    }
                    for (ServerPlayer sp : level.players()) {
                        PacketDistributor.sendToPlayer(sp, new SyncDoorLocksPayload(map));
                    }
                    LOG.info("[Locksmith] Cleanup triggered resync in level={} players={} locksNow={}",
                            level.dimension().location(), level.players().size(), map.size());
                }
            }
        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithDoorEvents] onServerTick failed (non-fatal).", t);
        }
    }

    private static void broadcastRemoveToLevelPlayers(ServerLevel level, long posLong) {
        try {
            if (level == null) return;
            for (ServerPlayer sp : level.players()) {
                PacketDistributor.sendToPlayer(sp, new RemoveDoorLockPayload(posLong));
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith] broadcastRemoveToLevelPlayers failed (non-fatal).", t);
        }
    }

    private static void safeDenyVanillaUse(PlayerInteractEvent.RightClickBlock event) {
        try {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
        } catch (Throwable t) {
            LOG.debug("[Locksmith] safeDenyVanillaUse: unable to set use flags (non-fatal).", t);
        }
    }

    private static void sendDeniedMessageThrottled(ServerPlayer player, long gameTime) {
        try {
            if (player == null) return;

            String id = player.getStringUUID();
            long last = DENY_THROTTLE.getOrDefault(id, -9999L);

            if (gameTime - last < 20) return;
            DENY_THROTTLE.put(id, gameTime);

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
