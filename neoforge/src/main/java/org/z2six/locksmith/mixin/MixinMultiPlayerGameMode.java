// MainFile: neoforge/src/main/java/org/z2six/locksmith/mixin/MixinMultiPlayerGameMode.java
package org.z2six.locksmith.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.z2six.locksmith.client.ClientHudMessages;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.lock.ChestLockManager;
import org.z2six.locksmith.lock.DoorLockManager;
import org.z2six.locksmith.network.LockChestPayload;
import org.z2six.locksmith.network.LockDoorPayload;
import org.z2six.locksmith.render.ClientChestLockState;
import org.z2six.locksmith.render.ClientChestOpenBlocker;
import org.z2six.locksmith.render.ClientDoorLockState;
import org.z2six.locksmith.render.ClientDoorOpenBlocker;
import org.z2six.locksmith.render.profile.ClientLockRenderProfiles;
import org.z2six.locksmith.render.profile.LockRenderProfile;
import org.z2six.locksmith.render.profile.LockTargetType;

@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {

    @Unique
    private static final Logger LOCKSMITH$LOG = LogUtils.getLogger();

    @Unique
    private static final int LOCKSMITH$CLIENT_BLOCK_OPEN_TICKS_AFTER_LOCK_CLICK = 8;

    @Unique
    private static volatile long LOCKSMITH$LAST_DENY_DOOR_TICK = -999999L;

    @Unique
    private static volatile long LOCKSMITH$LAST_DENY_CHEST_TICK = -999999L;

    @Unique
    private static volatile long LOCKSMITH$LAST_SUCCESS_DOOR_TICK = -999999L;

    @Unique
    private static volatile long LOCKSMITH$LAST_SUCCESS_CHEST_TICK = -999999L;

    @Unique
    private static final int LOCKSMITH$MSG_THROTTLE_TICKS = 12;

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, require = 0)
    private void locksmith$useItemOn(LocalPlayer player,
                                     InteractionHand hand,
                                     BlockHitResult hit,
                                     CallbackInfoReturnable<InteractionResult> cir) {
        try {
            if (cir == null) return;
            if (player == null || hit == null || hand == null) return;

            Level level = player.level();
            if (level == null) return;
            if (!level.isClientSide) return;

            if (hand != InteractionHand.MAIN_HAND) return;

            BlockPos clickedPos = hit.getBlockPos();
            if (clickedPos == null) return;

            BlockState clickedState;
            try {
                clickedState = level.getBlockState(clickedPos);
            } catch (Throwable t) {
                LOCKSMITH$LOG.warn("[Locksmith][MixinMultiPlayerGameMode] Failed reading clicked blockstate (non-fatal). pos={}", clickedPos, t);
                return;
            }

            if (clickedState == null || clickedState.isAir()) return;

            // DOOR path --------------------------------------------------------------------
            if (clickedState.getBlock() instanceof DoorBlock) {
                BlockPos doorPos = DoorLockManager.normalizeDoorPos(level, clickedPos, clickedState);
                long doorLong = doorPos.asLong();

                // Deny opening locked door without key (predicted-use path)
                if (ClientDoorLockState.isLocked(doorLong)) {
                    String requiredHash = ClientDoorLockState.getRequiredHash(doorLong);
                    if (requiredHash != null && !requiredHash.isBlank()) {
                        boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(player, requiredHash);
                        if (!hasKey) {
                            cir.setReturnValue(InteractionResult.FAIL);

                            // ✅ Actually show the message from the mixin path that is running.
                            locksmith$maybeShowDoorDenied(level);

                            if (LOCKSMITH$LOG.isInfoEnabled()) {
                                LOCKSMITH$LOG.info("[Locksmith][MixinMultiPlayerGameMode] Denied locked door predicted-use (no key). doorPos={}", doorPos);
                            }
                            return;
                        }
                    }
                    // Has key -> allow vanilla flow; do not eat interaction.
                    return;
                }

                // Lock registration click: iron key + registered + door currently unlocked
                ItemStack held = player.getMainHandItem();
                if (held == null || held.isEmpty()) return;
                if (!(held.getItem() instanceof IronKeyItem)) return;
                if (!IronKeyItem.isRegistered(held)) return;

                ClientDoorOpenBlocker.blockOpenForTicks(
                        doorLong,
                        level.getGameTime(),
                        LOCKSMITH$CLIENT_BLOCK_OPEN_TICKS_AFTER_LOCK_CLICK
                );

                try {
                    DoorLockManager.forceCloseDoorClient(level, doorPos);
                } catch (Throwable t) {
                    LOCKSMITH$LOG.debug("[Locksmith][MixinMultiPlayerGameMode] forceCloseDoorClient failed (non-fatal). doorPos={}", doorPos, t);
                }

                try {
                    PacketDistributor.sendToServer(new LockDoorPayload(doorLong));
                } catch (Throwable t) {
                    LOCKSMITH$LOG.error("[Locksmith][MixinMultiPlayerGameMode] Failed to send LockDoorPayload (non-fatal). doorPos={}", doorPos, t);
                }

                // Eat predicted interaction
                cir.setReturnValue(InteractionResult.SUCCESS);

                // ✅ Actually show success message from the mixin path.
                locksmith$maybeShowDoorSuccess(level);

                if (LOCKSMITH$LOG.isInfoEnabled()) {
                    String name = safeName(player);
                    LOCKSMITH$LOG.info("[Locksmith] Ate predicted door use to register lock (client). player={} doorPos={}", name, doorPos);
                }
                return;
            }

            // CHEST path -------------------------------------------------------------------
            ResourceLocation blockId = null;
            try {
                blockId = BuiltInRegistries.BLOCK.getKey(clickedState.getBlock());
            } catch (Throwable ignored) {
            }

            // Client-side gating: only configured "type=chest" blocks.
            LockRenderProfile prof = (blockId == null) ? null : ClientLockRenderProfiles.get(blockId);
            if (prof == null || !prof.isValid() || prof.type != LockTargetType.CHEST) {
                return;
            }

            BlockPos chestKeyPos = ChestLockManager.normalizeChestPos(level, clickedPos, clickedState);
            long chestKeyLong = chestKeyPos.asLong();

            // Deny opening locked chest without key (predicted-use path)
            if (ClientChestLockState.isLocked(chestKeyLong)) {
                String requiredHash = ClientChestLockState.getRequiredHash(chestKeyLong);
                if (requiredHash != null && !requiredHash.isBlank()) {
                    boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(player, requiredHash);
                    if (!hasKey) {
                        cir.setReturnValue(InteractionResult.FAIL);

                        // ✅ Actually show the message from the mixin path that is running.
                        locksmith$maybeShowChestDenied(level);

                        if (LOCKSMITH$LOG.isInfoEnabled()) {
                            LOCKSMITH$LOG.info("[Locksmith][MixinMultiPlayerGameMode] Denied locked chest predicted-use (no key). chestKeyPos={}", chestKeyPos);
                        }
                        return;
                    }
                }
                // Has key -> allow vanilla flow
                return;
            }

            // Lock registration click: iron key + registered + chest currently unlocked
            ItemStack held = player.getMainHandItem();
            if (held == null || held.isEmpty()) return;
            if (!(held.getItem() instanceof IronKeyItem)) return;
            if (!IronKeyItem.isRegistered(held)) return;

            ClientChestOpenBlocker.blockOpenForTicks(
                    chestKeyLong,
                    level.getGameTime(),
                    LOCKSMITH$CLIENT_BLOCK_OPEN_TICKS_AFTER_LOCK_CLICK
            );

            try {
                PacketDistributor.sendToServer(new LockChestPayload(chestKeyLong));
            } catch (Throwable t) {
                LOCKSMITH$LOG.error("[Locksmith][MixinMultiPlayerGameMode] Failed to send LockChestPayload (non-fatal). chestKeyPos={}", chestKeyPos, t);
            }

            cir.setReturnValue(InteractionResult.SUCCESS);

            // ✅ Actually show success message from the mixin path.
            locksmith$maybeShowChestSuccess(level);

            if (LOCKSMITH$LOG.isInfoEnabled()) {
                String name = safeName(player);
                LOCKSMITH$LOG.info("[Locksmith] Ate predicted chest use to register lock (client). player={} chestKeyPos={}", name, chestKeyPos);
            }

        } catch (Throwable t) {
            LOCKSMITH$LOG.error("[Locksmith][MixinMultiPlayerGameMode] useItemOn intercept failed (non-fatal).", t);
        }
    }

    // ------------------------------------------------------------------------
    // HUD message helpers (throttled)
    // ------------------------------------------------------------------------

    @Unique
    private static void locksmith$maybeShowDoorDenied(Level level) {
        try {
            if (level == null) return;
            long now = level.getGameTime();
            if (now - LOCKSMITH$LAST_DENY_DOOR_TICK < LOCKSMITH$MSG_THROTTLE_TICKS) return;
            LOCKSMITH$LAST_DENY_DOOR_TICK = now;

            ClientHudMessages.showDoorLockedNoKey();

            if (LOCKSMITH$LOG.isInfoEnabled()) {
                LOCKSMITH$LOG.info("[Locksmith][MixinMultiPlayerGameMode] Triggered HUD deny message (door_locked_no_key). tick={}", now);
            }
        } catch (Throwable t) {
            LOCKSMITH$LOG.warn("[Locksmith][MixinMultiPlayerGameMode] locksmith$maybeShowDoorDenied failed (non-fatal).", t);
        }
    }

    @Unique
    private static void locksmith$maybeShowChestDenied(Level level) {
        try {
            if (level == null) return;
            long now = level.getGameTime();
            if (now - LOCKSMITH$LAST_DENY_CHEST_TICK < LOCKSMITH$MSG_THROTTLE_TICKS) return;
            LOCKSMITH$LAST_DENY_CHEST_TICK = now;

            ClientHudMessages.showChestLockedNoKey();

            if (LOCKSMITH$LOG.isInfoEnabled()) {
                LOCKSMITH$LOG.info("[Locksmith][MixinMultiPlayerGameMode] Triggered HUD deny message (chest_locked_no_key). tick={}", now);
            }
        } catch (Throwable t) {
            LOCKSMITH$LOG.warn("[Locksmith][MixinMultiPlayerGameMode] locksmith$maybeShowChestDenied failed (non-fatal).", t);
        }
    }

    @Unique
    private static void locksmith$maybeShowDoorSuccess(Level level) {
        try {
            if (level == null) return;
            long now = level.getGameTime();
            if (now - LOCKSMITH$LAST_SUCCESS_DOOR_TICK < LOCKSMITH$MSG_THROTTLE_TICKS) return;
            LOCKSMITH$LAST_SUCCESS_DOOR_TICK = now;

            ClientHudMessages.showDoorLockSuccess();

            if (LOCKSMITH$LOG.isInfoEnabled()) {
                LOCKSMITH$LOG.info("[Locksmith][MixinMultiPlayerGameMode] Triggered HUD success message (door_locked_success). tick={}", now);
            }
        } catch (Throwable t) {
            LOCKSMITH$LOG.warn("[Locksmith][MixinMultiPlayerGameMode] locksmith$maybeShowDoorSuccess failed (non-fatal).", t);
        }
    }

    @Unique
    private static void locksmith$maybeShowChestSuccess(Level level) {
        try {
            if (level == null) return;
            long now = level.getGameTime();
            if (now - LOCKSMITH$LAST_SUCCESS_CHEST_TICK < LOCKSMITH$MSG_THROTTLE_TICKS) return;
            LOCKSMITH$LAST_SUCCESS_CHEST_TICK = now;

            ClientHudMessages.showChestLockSuccess();

            if (LOCKSMITH$LOG.isInfoEnabled()) {
                LOCKSMITH$LOG.info("[Locksmith][MixinMultiPlayerGameMode] Triggered HUD success message (chest_locked_success). tick={}", now);
            }
        } catch (Throwable t) {
            LOCKSMITH$LOG.warn("[Locksmith][MixinMultiPlayerGameMode] locksmith$maybeShowChestSuccess failed (non-fatal).", t);
        }
    }

    @Unique
    private static String safeName(Player p) {
        try {
            return p == null ? "null" : p.getName().getString();
        } catch (Throwable t) {
            return "<unknown>";
        }
    }
}
