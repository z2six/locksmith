// MainFile: neoforge/src/main/java/org/z2six/locksmith/mixin/MixinDoorBlock.java
package org.z2six.locksmith.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.z2six.locksmith.client.ClientHudMessages;
import org.z2six.locksmith.lock.DoorLockManager;
import org.z2six.locksmith.render.ClientDoorLockState;
import org.z2six.locksmith.render.ClientDoorOpenBlocker;

/**
 * Defensive door hooks:
 * - Deny opening locked doors without a matching key (client-side safety).
 * - Cancel predicted open via setOpen during our "blocker" window.
 *
 * NOTE:
 * Lock registration click is handled authoritatively by MixinMultiPlayerGameMode.
 * We intentionally do NOT send LockDoorPayload from here to avoid duplicate sends.
 */
@Mixin(DoorBlock.class)
public class MixinDoorBlock {

    private static final Logger LOG = LogUtils.getLogger();

    @Unique
    private static volatile long LOCKSMITH$LAST_DENY_MSG_TICK = -999999L;

    @Unique
    private static volatile long LOCKSMITH$LAST_BLOCKER_MSG_TICK = -999999L;

    @Unique
    private static final int LOCKSMITH$MSG_THROTTLE_TICKS = 12;

    static {
        try {
            LOG.info("[Locksmith][MixinDoorBlock] LOADED (DoorBlock defensive hooks active)");
        } catch (Throwable ignored) {
        }
    }

    /**
     * Empty-hand interaction path:
     * Prevent opening locked doors without a key client-side.
     *
     * Signature used by DoorBlock in 1.21.x:
     * useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult) -> InteractionResult
     */
    @Inject(
            method = "useWithoutItem(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void locksmith$useWithoutItem(BlockState state,
                                          Level level,
                                          BlockPos pos,
                                          Player player,
                                          net.minecraft.world.phys.BlockHitResult hit,
                                          CallbackInfoReturnable<InteractionResult> cir) {
        try {
            if (cir == null) return;
            if (level == null || pos == null || player == null || state == null) return;
            if (!level.isClientSide) return;
            if (!(state.getBlock() instanceof DoorBlock)) return;

            BlockPos doorPos = DoorLockManager.normalizeDoorPos(level, pos, state);
            long doorLong = doorPos.asLong();

            if (!ClientDoorLockState.isLocked(doorLong)) return;

            String requiredHash = ClientDoorLockState.getRequiredHash(doorLong);
            if (requiredHash == null || requiredHash.isBlank()) return;

            boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(player, requiredHash);
            if (!hasKey) {
                cir.setReturnValue(InteractionResult.FAIL);

                // ✅ This is the actual client-side deny path for empty-hand door use.
                locksmith$maybeShowDenied(level);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][MixinDoorBlock][Client] Denied empty-hand use on locked door (no key). pos={}", doorPos);
                }
            }
        } catch (Throwable t) {
            LOG.error("[Locksmith][MixinDoorBlock] useWithoutItem inject failed (non-fatal).", t);
        }
    }

    /**
     * Cancels predicted opens that happen via DoorBlock#setOpen(...) on client.
     * This is a safety net for the short "blocker" window after lock-click,
     * and for locked doors where a player lacks a key.
     */
    @Inject(
            method = "setOpen(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void locksmith$setOpen(Entity entity,
                                          Level level,
                                          BlockState state,
                                          BlockPos pos,
                                          boolean open,
                                          CallbackInfo ci) {
        try {
            if (ci == null) return;
            if (level == null || state == null || pos == null) return;

            if (!level.isClientSide) return;
            if (!open) return;
            if (!(state.getBlock() instanceof DoorBlock)) return;

            BlockPos doorLower = DoorLockManager.normalizeDoorPos(level, pos, state);
            long doorLong = doorLower.asLong();
            long now = level.getGameTime();

            // Block predicted opens for the short window after our "lock click".
            if (ClientDoorOpenBlocker.shouldBlockOpenNow(doorLong, now)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][MixinDoorBlock] CANCEL predicted open via setOpen at {} (blocker active).", doorLower);
                }

                // Optional UX: do NOT show "no key" here; blocker also triggers for the lock-click flow.
                locksmith$maybeShowBlocker(level);

                ci.cancel();
                DoorLockManager.forceCloseDoorClient(level, doorLower);
                return;
            }

            // Also deny predicted open for locked doors if the player doesn't have the key.
            if (ClientDoorLockState.isLocked(doorLong)) {
                String requiredHash = ClientDoorLockState.getRequiredHash(doorLong);
                if (requiredHash != null && !requiredHash.isBlank() && entity instanceof Player p) {
                    boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(p, requiredHash);
                    if (!hasKey) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("[Locksmith][MixinDoorBlock] CANCEL predicted open via setOpen at {} (locked + no key).", doorLower);
                        }

                        // ✅ This is the actual client-side deny path for predicted opens.
                        locksmith$maybeShowDenied(level);

                        ci.cancel();
                        DoorLockManager.forceCloseDoorClient(level, doorLower);
                    }
                }
            }

        } catch (Throwable t) {
            LOG.error("[Locksmith][MixinDoorBlock] setOpen inject failed (non-fatal).", t);
        }
    }

    // ------------------------------------------------------------------------
    // Message helpers (throttle to avoid spam/flicker)
    // ------------------------------------------------------------------------

    @Unique
    private static void locksmith$maybeShowDenied(Level level) {
        try {
            if (level == null) return;
            long now = level.getGameTime();

            if (now - LOCKSMITH$LAST_DENY_MSG_TICK < LOCKSMITH$MSG_THROTTLE_TICKS) {
                return;
            }
            LOCKSMITH$LAST_DENY_MSG_TICK = now;

            ClientHudMessages.showDoorLockedNoKey();

            if (LOG.isInfoEnabled()) {
                LOG.info("[Locksmith][MixinDoorBlock] Triggered HUD deny message (door_locked_no_key). tick={}", now);
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][MixinDoorBlock] locksmith$maybeShowDenied failed (non-fatal).", t);
        }
    }

    @Unique
    private static void locksmith$maybeShowBlocker(Level level) {
        try {
            if (level == null) return;
            long now = level.getGameTime();

            if (now - LOCKSMITH$LAST_BLOCKER_MSG_TICK < LOCKSMITH$MSG_THROTTLE_TICKS) {
                return;
            }
            LOCKSMITH$LAST_BLOCKER_MSG_TICK = now;

            // This is intentionally not a "no key" message. We keep it silent to avoid confusing the user.
            // If you later add a translation like "message.locksmith.door_busy", you can show it here.
            if (LOG.isDebugEnabled()) {
                LOG.debug("[Locksmith][MixinDoorBlock] Blocker prevented predicted open (no HUD message). tick={}", now);
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][MixinDoorBlock] locksmith$maybeShowBlocker failed (non-fatal).", t);
        }
    }
}
