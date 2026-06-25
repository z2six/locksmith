// MainFile: neoforge/src/main/java/org/z2six/locksmith/event/LocksmithChestEvents.java
package org.z2six.locksmith.event;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.lock.ChestLockManager;
import org.z2six.locksmith.lock.DoorLockManager;
import org.z2six.locksmith.network.AddChestLockPayload;
import org.z2six.locksmith.network.HudMessagePayload;
import org.z2six.locksmith.network.RemoveChestLockPayload;
import org.z2six.locksmith.network.SyncChestLocksPayload;
import org.z2six.locksmith.render.ClientChestLockState;
import org.z2six.locksmith.render.ClientChestOpenBlocker;
import org.z2six.locksmith.render.profile.ClientLockRenderProfiles;
import org.z2six.locksmith.render.profile.LockableBlockProfileService;
import org.z2six.locksmith.render.profile.LockRenderProfile;
import org.z2six.locksmith.render.profile.LockTargetType;
import org.z2six.locksmith.world.ChestLockSavedData;

import java.util.HashMap;
import java.util.Map;

public final class LocksmithChestEvents {

    private static final Logger LOG = Constants.LOG;

    private static final Map<String, Long> DENY_THROTTLE = new HashMap<>();

    private static final int CLIENT_BLOCK_OPEN_TICKS_AFTER_LOCK_CLICK = 8;

    private LocksmithChestEvents() {
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (event == null) return;
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;

            ServerLevel level = sp.serverLevel();
            if (level == null) return;

            ChestLockSavedData data = ChestLockSavedData.get(level);

            Map<Long, String> snap = data.snapshotLocks();
            var map = new Long2ObjectOpenHashMap<String>(snap.size());
            for (Map.Entry<Long, String> e : snap.entrySet()) {
                if (e.getValue() != null && !e.getValue().isBlank()) {
                    map.put(e.getKey(), e.getValue());
                }
            }

            PacketDistributor.sendToPlayer(sp, new SyncChestLocksPayload(map));
            LOG.info("[Locksmith] Sent SyncChestLocksPayload to {} (count={})", sp.getName().getString(), map.size());
        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithChestEvents] onPlayerLoggedIn failed (non-fatal).", t);
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (event == null) return;

            var player = event.getEntity();
            Level level = event.getLevel();
            if (player == null || level == null) return;

            BlockPos clickedPos = event.getPos();
            if (clickedPos == null) return;

            BlockState clickedState = level.getBlockState(clickedPos);
            if (clickedState == null || clickedState.isAir()) return;

            ResourceLocation blockId = null;
            try {
                blockId = BuiltInRegistries.BLOCK.getKey(clickedState.getBlock());
            } catch (Throwable ignored) {
            }

            if (level.isClientSide) {
                if (!isChestTypeConfiguredClient(blockId)) {
                    return;
                }
            } else {
                if (!isChestTypeConfiguredServer(blockId)) {
                    return;
                }
            }

            BlockPos chestKeyPos = ChestLockManager.normalizeChestPos(level, clickedPos, clickedState);
            long chestKeyLong = chestKeyPos.asLong();

            // ---------- CLIENT ----------
            if (level.isClientSide) {
                if (event.getHand() == InteractionHand.MAIN_HAND && player.isShiftKeyDown()) {
                    return;
                }

                ClientChestOpenBlocker.cleanupExpired(level.getGameTime(), 64);

                if (!ClientChestLockState.isLocked(chestKeyLong)
                        && event.getHand() == InteractionHand.MAIN_HAND) {

                    ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
                    if (held != null && !held.isEmpty()
                            && held.getItem() instanceof IronKeyItem
                            && IronKeyItem.isRegistered(held)) {

                        ClientChestOpenBlocker.blockOpenForTicks(
                                chestKeyLong,
                                level.getGameTime(),
                                CLIENT_BLOCK_OPEN_TICKS_AFTER_LOCK_CLICK
                        );

                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        safeDenyVanillaUse(event);

                        // NOTE: We do NOT show "success" here anymore; server will tell us via HudMessagePayload.
                        try {
                            PacketDistributor.sendToServer(new org.z2six.locksmith.network.LockChestPayload(chestKeyLong));
                            LOG.debug("[Locksmith][Client] Chest lock click ate interaction and sent LockChestPayload pos={}", chestKeyPos);
                        } catch (Throwable t) {
                            LOG.error("[Locksmith][Client] Failed to send LockChestPayload (non-fatal).", t);
                        }
                        return;
                    }
                }

                if (ClientChestLockState.isLocked(chestKeyLong)) {
                    String requiredHash = ClientChestLockState.getRequiredHash(chestKeyLong);
                    if (requiredHash != null && !requiredHash.isBlank()) {
                        boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(player, requiredHash);
                        if (!hasKey) {
                            event.setCanceled(true);
                            event.setCancellationResult(InteractionResult.FAIL);
                            safeDenyVanillaUse(event);

                            // Optional: client can show instantly; server will also send for dedicated consistency.
                            org.z2six.locksmith.client.ClientHudMessages.showChestLockedNoKey();

                            LOG.debug("[Locksmith][Client] Blocked chest open at {} (no matching key).", chestKeyPos);
                        }
                    }
                }

                return;
            }

            // ---------- SERVER ----------
            if (!(player instanceof ServerPlayer sp)) return;
            ServerLevel sLevel = (ServerLevel) level;

            ChestLockSavedData data = ChestLockSavedData.get(sLevel);

            BlockState keyState = sLevel.getBlockState(chestKeyPos);
            if (keyState == null || keyState.isAir()) {
                boolean removed = data.removeLockLong(chestKeyLong);
                if (removed) {
                    broadcastRemoveToLevelPlayers(sLevel, chestKeyLong);
                    LOG.info("[Locksmith] Removed chest lock at {} because block disappeared.", chestKeyPos);
                }
                return;
            }

            boolean isLocked = data.isLockedLong(chestKeyLong);

            // Unlocked -> locking via interact event (can still happen in some MP paths)
            if (!isLocked && event.getHand() == InteractionHand.MAIN_HAND) {
                ItemStack held = sp.getItemInHand(InteractionHand.MAIN_HAND);
                if (held != null && !held.isEmpty()
                        && held.getItem() instanceof IronKeyItem
                        && IronKeyItem.isRegistered(held)) {

                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    safeDenyVanillaUse(event);

                    boolean added = ChestLockManager.tryLockChestWithHeldKey(sLevel, sp, chestKeyPos, held);
                    if (added) {
                        String hash = data.getHashLong(chestKeyLong);
                        for (ServerPlayer other : sLevel.players()) {
                            PacketDistributor.sendToPlayer(other, new AddChestLockPayload(chestKeyLong, hash));
                        }

                        // ✅ Dedicated-safe HUD message: S2C
                        try {
                            PacketDistributor.sendToPlayer(sp, new HudMessagePayload(HudMessagePayload.CHEST_LOCK_SUCCESS));
                            LOG.info("[Locksmith][ChestMessages] Sent CHEST_LOCK_SUCCESS HUD payload to {} at {}.",
                                    sp.getName().getString(), chestKeyPos);
                        } catch (Throwable t) {
                            LOG.warn("[Locksmith][ChestMessages] Failed sending CHEST_LOCK_SUCCESS HUD payload (non-fatal).", t);
                        }

                        LOG.info("[Locksmith] Chest locked at {} (sync sent, interaction eaten).", chestKeyPos);
                    } else {
                        LOG.debug("[Locksmith] Chest lock attempt at {} ate interaction but did not add lock (already locked or invalid key).",
                                chestKeyPos);
                    }

                    return;
                }
            }

            // Locked -> permission check
            if (data.isLockedLong(chestKeyLong)) {
                String requiredHash = data.getHashLong(chestKeyLong);
                if (requiredHash == null || requiredHash.isBlank()) {
                    LOG.warn("[Locksmith] Chest at {} marked locked but has blank hash. Allowing interaction.", chestKeyPos);
                    return;
                }

                boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(sp, requiredHash);
                if (!hasKey) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);
                    safeDenyVanillaUse(event);

                    sendDeniedMessageThrottled(sp, sLevel.getGameTime(), chestKeyPos);

                    LOG.debug("[Locksmith] Blocked chest open at {} for player {} (no matching key).",
                            chestKeyPos, sp.getName().getString());
                }
            }

        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithChestEvents] onRightClickBlock failed (non-fatal).", t);
        }
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        try {
            if (event == null) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;

            BlockPos pos = event.getPos();
            if (pos == null) return;

            BlockState st = level.getBlockState(pos);
            if (st == null || st.isAir()) return;

            ResourceLocation blockId = null;
            try {
                blockId = BuiltInRegistries.BLOCK.getKey(st.getBlock());
            } catch (Throwable ignored) {
            }

            if (!isChestTypeConfiguredServer(blockId)) return;

            BlockPos chestKeyPos = ChestLockManager.normalizeChestPos(level, pos, st);
            long keyLong = chestKeyPos.asLong();

            ChestLockSavedData data = ChestLockSavedData.get(level);
            boolean removed = data.removeLockLong(keyLong);
            if (removed) {
                broadcastRemoveToLevelPlayers(level, keyLong);
                LOG.info("[Locksmith] Removed chest lock due to player break at chestKeyPos={}", chestKeyPos);
            }

        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithChestEvents] onBlockBreak failed (non-fatal).", t);
        }
    }

    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        try {
            if (event == null) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            if (event.getAffectedBlocks() == null || event.getAffectedBlocks().isEmpty()) return;

            ChestLockSavedData data = ChestLockSavedData.get(level);

            int removed = 0;
            for (BlockPos pos : event.getAffectedBlocks()) {
                if (pos == null) continue;

                BlockState st = level.getBlockState(pos);
                if (st == null || st.isAir()) continue;

                ResourceLocation blockId = null;
                try {
                    blockId = BuiltInRegistries.BLOCK.getKey(st.getBlock());
                } catch (Throwable ignored) {
                }

                if (!isChestTypeConfiguredServer(blockId)) continue;

                BlockPos chestKeyPos = ChestLockManager.normalizeChestPos(level, pos, st);
                long keyLong = chestKeyPos.asLong();

                boolean did = data.removeLockLong(keyLong);
                if (did) {
                    broadcastRemoveToLevelPlayers(level, keyLong);
                    removed++;
                }
            }

            if (removed > 0) {
                LOG.info("[Locksmith] Removed {} chest lock(s) due to explosion detonate.", removed);
            }

        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithChestEvents] onExplosionDetonate failed (non-fatal).", t);
        }
    }

    private static void broadcastRemoveToLevelPlayers(ServerLevel level, long posLong) {
        try {
            if (level == null) return;
            for (ServerPlayer sp : level.players()) {
                PacketDistributor.sendToPlayer(sp, new RemoveChestLockPayload(posLong));
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith] broadcastRemoveToLevelPlayers (chest) failed (non-fatal).", t);
        }
    }

    private static void safeDenyVanillaUse(PlayerInteractEvent.RightClickBlock event) {
        try {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
        } catch (Throwable t) {
            LOG.debug("[Locksmith] safeDenyVanillaUse (chest): unable to set use flags (non-fatal).", t);
        }
    }

    private static void sendDeniedMessageThrottled(ServerPlayer player, long gameTime, BlockPos chestPos) {
        try {
            if (player == null) return;

            String id = player.getStringUUID();
            long last = DENY_THROTTLE.getOrDefault(id, -9999L);

            if (gameTime - last < 20) return;
            DENY_THROTTLE.put(id, gameTime);

            // ✅ Dedicated-safe HUD message: S2C
            try {
                PacketDistributor.sendToPlayer(player, new HudMessagePayload(HudMessagePayload.CHEST_LOCKED_NO_KEY));
                LOG.info("[Locksmith][ChestMessages] Sent CHEST_LOCKED_NO_KEY HUD payload to {} at {} (throttled).",
                        player.getName().getString(), chestPos);
            } catch (Throwable t) {
                LOG.warn("[Locksmith][ChestMessages] Failed sending CHEST_LOCKED_NO_KEY HUD payload (non-fatal).", t);
            }

        } catch (Throwable t) {
            LOG.warn("[Locksmith] sendDeniedMessageThrottled (chest) failed (non-fatal).", t);
        }
    }

    private static boolean isChestTypeConfiguredClient(ResourceLocation blockId) {
        try {
            if (blockId == null) return false;
            LockRenderProfile prof = ClientLockRenderProfiles.get(blockId);
            return prof != null && prof.isValid() && prof.type == LockTargetType.CHEST;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][LocksmithChestEvents] isChestTypeConfiguredClient failed (non-fatal). blockId={}", blockId, t);
            return false;
        }
    }

    private static boolean isChestTypeConfiguredServer(ResourceLocation blockId) {
        try {
            if (blockId == null) return false;

            return LockableBlockProfileService.isLockableChest(blockId);
        } catch (Throwable t) {
            LOG.warn("[Locksmith][LocksmithChestEvents] isChestTypeConfiguredServer failed (non-fatal). blockId={}", blockId, t);
            return false;
        }
    }
}
