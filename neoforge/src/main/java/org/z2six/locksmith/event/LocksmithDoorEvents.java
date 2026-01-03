// MainFile: LocksmithDoorEvents.java
package org.z2six.locksmith.event;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.config.LockProfileConfig;
import org.z2six.locksmith.config.LocksmithClientConfig;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.lock.DoorLockManager;
import org.z2six.locksmith.network.AddDoorLockPayload;
import org.z2six.locksmith.network.LockDoorPayload;
import org.z2six.locksmith.network.RemoveDoorLockPayload;
import org.z2six.locksmith.network.SyncDoorLocksPayload;
import org.z2six.locksmith.render.ClientDoorLockState;
import org.z2six.locksmith.render.ClientDoorOpenBlocker;
import org.z2six.locksmith.world.DoorLockSavedData;
import org.z2six.locksmith.client.ClientHudMessages;

import java.util.HashMap;
import java.util.Map;

public final class LocksmithDoorEvents {

    private static final Logger LOG = Constants.LOG;

    private static final Map<String, Long> DENY_THROTTLE = new HashMap<>();
    private static final int CLEANUP_EVERY_TICKS = 200;
    private static final int CLEANUP_MAX_CHECK_PER_PASS = 512;

    private static final int CLIENT_BLOCK_OPEN_TICKS_AFTER_LOCK_CLICK = 6;

    // Existing “keep closed for a few ticks” mechanic
    private static final int FORCE_CLOSE_AFTER_LOCK_TICKS = 5;
    private static final int FORCE_CLOSE_MAX_PER_TICK = 256;

    // New: max auto-close operations per tick per level
    private static final int AUTO_CLOSE_MAX_PER_TICK = 256;

    /**
     * Auto-close scheduler:
     * - key: dimension -> (doorPosLong -> ticksRemaining)
     * - when ticksRemaining hits 0, we close once and remove.
     */
    private static final Map<ResourceKey<Level>, Long2IntOpenHashMap> AUTO_CLOSE = new HashMap<>();

    private LocksmithDoorEvents() {
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            LockProfileConfig.ensureDefaultFileExists();

            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            ServerLevel level = sp.serverLevel();

            DoorLockSavedData data = DoorLockSavedData.get(level);
            data.cleanupInvalidDoors(level, CLEANUP_MAX_CHECK_PER_PASS);

            Map<Long, String> snap = data.snapshotLocks();
            var map = new Long2ObjectOpenHashMap<String>(snap.size());
            for (Map.Entry<Long, String> e : snap.entrySet()) {
                if (e.getValue() != null && !e.getValue().isBlank()) {
                    map.put(e.getKey(), e.getValue());
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

            // =========================
            // CLIENT-SIDE PREDICTION
            // =========================
            if (level.isClientSide) {
                long doorLong = doorPos.asLong();

                ClientDoorOpenBlocker.cleanupExpired(level.getGameTime(), 64);

                // 1) Lock placement (client-side prediction)
                if (!ClientDoorLockState.isLocked(doorLong)
                        && event.getHand() == InteractionHand.MAIN_HAND) {

                    ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
                    if (held != null && !held.isEmpty()
                            && held.getItem() instanceof IronKeyItem
                            && IronKeyItem.isRegistered(held)) {

                        ClientDoorOpenBlocker.blockOpenForTicks(
                                doorLong,
                                level.getGameTime(),
                                CLIENT_BLOCK_OPEN_TICKS_AFTER_LOCK_CLICK
                        );

                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        safeDenyVanillaUse(event);

                        try {
                            PacketDistributor.sendToServer(new LockDoorPayload(doorLong));
                            ClientHudMessages.showDoorLockSuccess();
                            LOG.debug("[Locksmith][Client] Lock click ate interaction and sent LockDoorPayload pos={}", doorPos);
                        } catch (Throwable t) {
                            LOG.error("[Locksmith][Client] Failed to send LockDoorPayload (non-fatal).", t);
                        }
                        return;
                    }
                }

                // 2) Locked door: client denies if no key
                if (ClientDoorLockState.isLocked(doorLong)) {
                    String requiredHash = ClientDoorLockState.getRequiredHash(doorLong);
                    if (requiredHash != null && !requiredHash.isBlank()) {
                        boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(player, requiredHash);
                        if (!hasKey) {
                            event.setCanceled(true);
                            event.setCancellationResult(InteractionResult.FAIL);
                            safeDenyVanillaUse(event);
                            ClientHudMessages.showDoorLockedNoKey();
                            LOG.debug("[Locksmith][Client] Denied locked door use at {} (no key).", doorPos);
                        }
                    }
                }

                return;
            }

            // =========================
            // SERVER-SIDE AUTHORITATIVE
            // =========================
            if (!(player instanceof ServerPlayer sp)) {
                return;
            }
            ServerLevel sLevel = (ServerLevel) level;

            DoorLockSavedData data = DoorLockSavedData.get(sLevel);

            // If the door got removed, clean lock
            if (!(sLevel.getBlockState(doorPos).getBlock() instanceof DoorBlock)) {
                boolean removed = data.removeLock(doorPos);
                if (removed) {
                    broadcastRemoveToLevelPlayers(sLevel, doorPos.asLong());
                }
                return;
            }

            boolean isLocked = data.isLocked(doorPos);

            // 1) Unlocked door: handle lock placement with iron key
            if (!isLocked && event.getHand() == InteractionHand.MAIN_HAND) {
                ItemStack held = sp.getItemInHand(InteractionHand.MAIN_HAND);
                if (held != null && !held.isEmpty()
                        && held.getItem() instanceof IronKeyItem
                        && IronKeyItem.isRegistered(held)) {

                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    safeDenyVanillaUse(event);

                    boolean added = DoorLockManager.tryLockDoorWithHeldKey(sLevel, sp, doorPos, held);

                    // After locking, keep door forcibly closed for a few ticks to defeat prediction races
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

            // 2) Locked door: permission check + optional auto-close
            if (data.isLocked(doorPos)) {
                String requiredHash = data.getHash(doorPos);
                if (requiredHash == null || requiredHash.isBlank()) {
                    LOG.warn("[Locksmith] Door at {} marked locked but has blank hash. Allowing interaction.", doorPos);
                    return;
                }

                boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(sp, requiredHash);
                if (!hasKey) {
                    // No key -> deny open
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);
                    safeDenyVanillaUse(event);

                    sendDeniedMessageThrottled(sp, sLevel.getGameTime());

                    LOG.debug("[Locksmith] Blocked door open at {} for player {} (no matching key).",
                            doorPos, sp.getName().getString());
                } else {
                    // Has key -> allow vanilla toggle, but possibly schedule auto-close
                    try {
                        if (LocksmithClientConfig.isAutoCloseEnabled()) {
                            int ticks = LocksmithClientConfig.getAutoCloseTicks();
                            if (ticks > 0) {
                                boolean isCurrentlyOpen = false;
                                try {
                                    if (doorState.hasProperty(DoorBlock.OPEN)) {
                                        isCurrentlyOpen = doorState.getValue(DoorBlock.OPEN);
                                    }
                                } catch (Throwable t) {
                                    LOG.warn("[Locksmith] Failed to read door OPEN property at {} (non-fatal).", doorPos, t);
                                }

                                // Only schedule auto-close when the door is currently CLOSED
                                // and this click is going to OPEN it.
                                if (!isCurrentlyOpen) {
                                    scheduleAutoCloseLockedDoor(sLevel, doorPos, ticks);
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug(
                                                "[Locksmith] Scheduled auto-close for locked door at {} in {} tick(s).",
                                                doorPos, ticks
                                        );
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {
                        LOG.warn("[Locksmith] Auto-close scheduling failed at {} (non-fatal).", doorPos, t);
                    }
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

            MinecraftServer server = event.getServer();
            if (server == null) return;

            // 1) Existing force-close ticks (keep door closed for a short window)
            for (ServerLevel level : server.getAllLevels()) {
                DoorLockManager.tickForceClose(level, FORCE_CLOSE_MAX_PER_TICK);
                processAutoClose(level, AUTO_CLOSE_MAX_PER_TICK);
            }

            // 2) Periodic cleanup / resync of invalid doors
            ServerLevel overworld = server.overworld();
            if (overworld == null) return;

            long gameTime = overworld.getGameTime();
            if (gameTime % CLEANUP_EVERY_TICKS != 0) return;

            for (ServerLevel level : server.getAllLevels()) {
                DoorLockSavedData data = DoorLockSavedData.get(level);
                int removedCount = data.cleanupInvalidDoors(level, CLEANUP_MAX_CHECK_PER_PASS);
                if (removedCount > 0) {
                    Map<Long, String> snap = data.snapshotLocks();
                    var map = new Long2ObjectOpenHashMap<String>(snap.size());
                    for (Map.Entry<Long, String> e : snap.entrySet()) {
                        if (e.getValue() != null && !e.getValue().isBlank()) {
                            map.put(e.getKey(), e.getValue());
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

    // =========================
    // Auto-close helpers
    // =========================

    private static void scheduleAutoCloseLockedDoor(ServerLevel level, BlockPos doorPos, int ticks) {
        try {
            if (level == null || doorPos == null) return;
            if (ticks <= 0) ticks = 1;

            ResourceKey<Level> dim = level.dimension();
            Long2IntOpenHashMap map = AUTO_CLOSE.computeIfAbsent(dim, k -> {
                Long2IntOpenHashMap m = new Long2IntOpenHashMap();
                m.defaultReturnValue(0);
                return m;
            });

            long key = doorPos.asLong();
            int prev = map.get(key);
            int next = Math.max(prev, ticks);
            map.put(key, next);

            if (LOG.isDebugEnabled()) {
                LOG.debug("[Locksmith] scheduleAutoClose dim={} pos={} ticks={}",
                        dim.location(), doorPos, ticks);
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith] scheduleAutoCloseLockedDoor failed (non-fatal).", t);
        }
    }

    private static void processAutoClose(ServerLevel level, int maxPerTick) {
        try {
            if (level == null) return;

            ResourceKey<Level> dim = level.dimension();
            Long2IntOpenHashMap map = AUTO_CLOSE.get(dim);
            if (map == null || map.isEmpty()) return;

            long[] keys = map.keySet().toLongArray();
            int processed = 0;

            for (long posLong : keys) {
                if (maxPerTick > 0 && processed >= maxPerTick) break;

                int remaining = map.get(posLong);
                if (remaining <= 0) {
                    map.remove(posLong);
                    continue;
                }

                remaining--;
                if (remaining <= 0) {
                    BlockPos pos = BlockPos.of(posLong);
                    DoorLockManager.forceCloseDoor(level, pos);
                    // After auto-close, keep closed briefly to defeat client prediction races.
                    DoorLockManager.requestForceClose(level, pos, FORCE_CLOSE_AFTER_LOCK_TICKS);
                    map.remove(posLong);
                    processed++;

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[Locksmith] Auto-close executed at dim={} pos={}", dim.location(), pos);
                    }
                } else {
                    map.put(posLong, remaining);
                }
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith] processAutoClose failed (non-fatal).", t);
        }
    }

    // =========================
    // Misc helpers
    // =========================

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
