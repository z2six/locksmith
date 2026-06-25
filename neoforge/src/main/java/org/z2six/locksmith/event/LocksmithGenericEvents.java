// MainFile: neoforge/src/main/java/org/z2six/locksmith/event/LocksmithGenericEvents.java
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
import org.z2six.locksmith.lock.DoorLockManager;
import org.z2six.locksmith.network.AddGenericLockPayload;
import org.z2six.locksmith.network.HudMessagePayload;
import org.z2six.locksmith.network.LockGenericPayload;
import org.z2six.locksmith.network.PulseGenericLockPayload;
import org.z2six.locksmith.network.RemoveGenericLockPayload;
import org.z2six.locksmith.network.SyncGenericLocksPayload;
import org.z2six.locksmith.render.ClientGenericLockState;
import org.z2six.locksmith.render.profile.ClientLockRenderProfiles;
import org.z2six.locksmith.render.profile.LockRenderProfile;
import org.z2six.locksmith.render.profile.LockTargetType;
import org.z2six.locksmith.render.profile.LockableBlockProfileService;
import org.z2six.locksmith.world.GenericLockSavedData;

import java.util.HashMap;
import java.util.Map;

public final class LocksmithGenericEvents {
    private static final Logger LOG = Constants.LOG;
    private static final Map<String, Long> DENY_THROTTLE = new HashMap<>();

    private LocksmithGenericEvents() {
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (event == null) return;
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;

            GenericLockSavedData data = GenericLockSavedData.get(sp.serverLevel());
            Map<Long, String> snap = data.snapshotLocks();
            var map = new Long2ObjectOpenHashMap<String>(snap.size());
            for (Map.Entry<Long, String> e : snap.entrySet()) {
                if (e.getValue() != null && !e.getValue().isBlank()) {
                    map.put(e.getKey(), e.getValue());
                }
            }

            PacketDistributor.sendToPlayer(sp, new SyncGenericLocksPayload(map));
            LOG.info("[Locksmith] Sent SyncGenericLocksPayload to {} (count={})", sp.getName().getString(), map.size());
        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithGenericEvents] onPlayerLoggedIn failed (non-fatal).", t);
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (event == null) return;

            var player = event.getEntity();
            Level level = event.getLevel();
            if (player == null || level == null) return;

            BlockPos pos = event.getPos();
            if (pos == null) return;

            BlockState state = level.getBlockState(pos);
            if (state == null || state.isAir()) return;

            ResourceLocation blockId = blockId(state);
            if (level.isClientSide) {
                if (!isGenericTypeConfiguredClient(blockId)) return;
            } else {
                if (!isGenericTypeConfiguredServer(blockId)) return;
            }

            long posLong = pos.asLong();

            if (level.isClientSide) {
                if (event.getHand() == InteractionHand.MAIN_HAND && player.isShiftKeyDown()) {
                    return;
                }

                if (!ClientGenericLockState.isLocked(posLong) && event.getHand() == InteractionHand.MAIN_HAND) {
                    ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
                    if (isRegisteredIronKey(held)) {
                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        safeDenyVanillaUse(event);

                        try {
                            PacketDistributor.sendToServer(new LockGenericPayload(posLong));
                            LOG.debug("[Locksmith][Client] Generic lock click ate interaction and sent LockGenericPayload pos={}", pos);
                        } catch (Throwable t) {
                            LOG.error("[Locksmith][Client] Failed to send LockGenericPayload (non-fatal).", t);
                        }
                        return;
                    }
                }

                if (ClientGenericLockState.isLocked(posLong)) {
                    String requiredHash = ClientGenericLockState.getRequiredHash(posLong);
                    if (requiredHash != null && !requiredHash.isBlank()) {
                        boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(player, requiredHash);
                        if (!hasKey) {
                            event.setCanceled(true);
                            event.setCancellationResult(InteractionResult.FAIL);
                            safeDenyVanillaUse(event);
                            org.z2six.locksmith.client.ClientHudMessages.showGenericLockedNoKey();
                            LOG.debug("[Locksmith][Client] Blocked generic block use at {} (no matching key).", pos);
                        } else {
                            org.z2six.locksmith.render.ClientGenericLockPulse.trigger(posLong, level.getGameTime());
                        }
                    }
                }
                return;
            }

            if (!(player instanceof ServerPlayer sp)) return;
            ServerLevel sLevel = (ServerLevel) level;
            GenericLockSavedData data = GenericLockSavedData.get(sLevel);

            BlockState currentState = sLevel.getBlockState(pos);
            if (currentState == null || currentState.isAir()) {
                boolean removed = data.removeLockLong(posLong);
                if (removed) broadcastRemoveToLevelPlayers(sLevel, posLong);
                return;
            }

            boolean isLocked = data.isLockedLong(posLong);

            if (!isLocked && event.getHand() == InteractionHand.MAIN_HAND) {
                ItemStack held = sp.getItemInHand(InteractionHand.MAIN_HAND);
                if (isRegisteredIronKey(held)) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    safeDenyVanillaUse(event);

                    String hash = IronKeyItem.getHashOrEmpty(held);
                    boolean added = data.putLockLong(posLong, hash);
                    if (added) {
                        for (ServerPlayer other : sLevel.players()) {
                            PacketDistributor.sendToPlayer(other, new AddGenericLockPayload(posLong, hash));
                        }
                        PacketDistributor.sendToPlayer(sp, new HudMessagePayload(HudMessagePayload.GENERIC_LOCK_SUCCESS));
                        LOG.info("[Locksmith] Generic block locked at {} (sync sent, interaction eaten).", pos);
                    }
                    return;
                }
            }

            if (data.isLockedLong(posLong)) {
                String requiredHash = data.getHashLong(posLong);
                if (requiredHash == null || requiredHash.isBlank()) {
                    LOG.warn("[Locksmith] Generic block at {} marked locked but has blank hash. Allowing interaction.", pos);
                    return;
                }

                boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(sp, requiredHash);
                if (!hasKey) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);
                    safeDenyVanillaUse(event);
                    sendDeniedMessageThrottled(sp, sLevel.getGameTime(), pos);
                    LOG.debug("[Locksmith] Blocked generic block use at {} for player {} (no matching key).",
                            pos, sp.getName().getString());
                } else if (event.getHand() == InteractionHand.MAIN_HAND) {
                    for (ServerPlayer other : sLevel.players()) {
                        PacketDistributor.sendToPlayer(other, new PulseGenericLockPayload(posLong));
                    }
                }
            }
        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithGenericEvents] onRightClickBlock failed (non-fatal).", t);
        }
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        try {
            if (event == null) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;

            BlockPos pos = event.getPos();
            if (pos == null) return;

            BlockState state = level.getBlockState(pos);
            if (state == null || state.isAir()) return;
            if (!isGenericTypeConfiguredServer(blockId(state))) return;

            long posLong = pos.asLong();
            boolean removed = GenericLockSavedData.get(level).removeLockLong(posLong);
            if (removed) {
                broadcastRemoveToLevelPlayers(level, posLong);
                LOG.info("[Locksmith] Removed generic lock due to player break at {}", pos);
            }
        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithGenericEvents] onBlockBreak failed (non-fatal).", t);
        }
    }

    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        try {
            if (event == null) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            if (event.getAffectedBlocks() == null || event.getAffectedBlocks().isEmpty()) return;

            GenericLockSavedData data = GenericLockSavedData.get(level);
            int removed = 0;
            for (BlockPos pos : event.getAffectedBlocks()) {
                if (pos == null) continue;
                BlockState state = level.getBlockState(pos);
                if (state == null || state.isAir()) continue;
                if (!isGenericTypeConfiguredServer(blockId(state))) continue;

                long posLong = pos.asLong();
                if (data.removeLockLong(posLong)) {
                    broadcastRemoveToLevelPlayers(level, posLong);
                    removed++;
                }
            }

            if (removed > 0) {
                LOG.info("[Locksmith] Removed {} generic lock(s) due to explosion detonate.", removed);
            }
        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithGenericEvents] onExplosionDetonate failed (non-fatal).", t);
        }
    }

    private static void broadcastRemoveToLevelPlayers(ServerLevel level, long posLong) {
        try {
            if (level == null) return;
            for (ServerPlayer sp : level.players()) {
                PacketDistributor.sendToPlayer(sp, new RemoveGenericLockPayload(posLong));
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith] broadcastRemoveToLevelPlayers (generic) failed (non-fatal).", t);
        }
    }

    private static void safeDenyVanillaUse(PlayerInteractEvent.RightClickBlock event) {
        try {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
        } catch (Throwable t) {
            LOG.debug("[Locksmith] safeDenyVanillaUse (generic): unable to set use flags (non-fatal).", t);
        }
    }

    private static void sendDeniedMessageThrottled(ServerPlayer player, long gameTime, BlockPos pos) {
        try {
            if (player == null) return;

            String id = player.getStringUUID();
            long last = DENY_THROTTLE.getOrDefault(id, -9999L);
            if (gameTime - last < 20) return;
            DENY_THROTTLE.put(id, gameTime);

            PacketDistributor.sendToPlayer(player, new HudMessagePayload(HudMessagePayload.GENERIC_LOCKED_NO_KEY));
            LOG.info("[Locksmith][GenericMessages] Sent GENERIC_LOCKED_NO_KEY HUD payload to {} at {} (throttled).",
                    player.getName().getString(), pos);
        } catch (Throwable t) {
            LOG.warn("[Locksmith] sendDeniedMessageThrottled (generic) failed (non-fatal).", t);
        }
    }

    private static boolean isGenericTypeConfiguredClient(ResourceLocation blockId) {
        try {
            if (blockId == null) return false;
            LockRenderProfile prof = ClientLockRenderProfiles.get(blockId);
            return prof != null && prof.isValid() && prof.type == LockTargetType.GENERIC;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][LocksmithGenericEvents] isGenericTypeConfiguredClient failed (non-fatal). blockId={}", blockId, t);
            return false;
        }
    }

    private static boolean isGenericTypeConfiguredServer(ResourceLocation blockId) {
        try {
            return blockId != null && LockableBlockProfileService.isLockableGeneric(blockId);
        } catch (Throwable t) {
            LOG.warn("[Locksmith][LocksmithGenericEvents] isGenericTypeConfiguredServer failed (non-fatal). blockId={}", blockId, t);
            return false;
        }
    }

    private static ResourceLocation blockId(BlockState state) {
        try {
            return state == null ? null : BuiltInRegistries.BLOCK.getKey(state.getBlock());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isRegisteredIronKey(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getItem() instanceof IronKeyItem
                && IronKeyItem.isRegistered(stack);
    }
}
