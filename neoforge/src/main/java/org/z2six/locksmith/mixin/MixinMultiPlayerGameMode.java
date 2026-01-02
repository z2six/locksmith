// MainFile: neoforge/src/main/java/org/z2six/locksmith/mixin/MixinMultiPlayerGameMode.java
package org.z2six.locksmith.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
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
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.lock.DoorLockManager;
import org.z2six.locksmith.network.LockDoorPayload;
import org.z2six.locksmith.render.ClientDoorLockState;
import org.z2six.locksmith.render.ClientDoorOpenBlocker;

/**
 * Intercepts the CLIENT predicted-use pipeline BEFORE vanilla can toggle doors open visually.
 *
 * Why this exists:
 * - In 1.21.x the client uses MultiPlayerGameMode#useItemOn -> performUseItemOn for prediction.
 * - Cancelling RightClickBlock is not always early enough to prevent the brief client-side door open.
 */
@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {

    @Unique
    private static final Logger LOCKSMITH$LOG = LogUtils.getLogger();

    // A bit longer than your 6 ticks; this is purely client visual safety.
    @Unique
    private static final int LOCKSMITH$CLIENT_BLOCK_OPEN_TICKS_AFTER_LOCK_CLICK = 8;

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

            // This mixin is only meaningful on the client prediction path.
            if (!level.isClientSide) return;

            // Only care about main hand for your lock-registration click.
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

            if (!(clickedState.getBlock() instanceof DoorBlock)) {
                return; // not a door -> let vanilla proceed
            }

            BlockPos doorPos = DoorLockManager.normalizeDoorPos(level, clickedPos, clickedState);
            long doorLong = doorPos.asLong();

            // ---- Case A: Door is locked client-side: deny if player lacks key (prevents client prediction too) ----
            if (ClientDoorLockState.isLocked(doorLong)) {
                String requiredHash = ClientDoorLockState.getRequiredHash(doorLong);
                if (requiredHash != null && !requiredHash.isBlank()) {
                    boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(player, requiredHash);
                    if (!hasKey) {
                        cir.setReturnValue(InteractionResult.FAIL);
                        if (LOCKSMITH$LOG.isDebugEnabled()) {
                            LOCKSMITH$LOG.debug("[Locksmith][MixinMultiPlayerGameMode] Denied locked door predicted-use (no key). doorPos={}", doorPos);
                        }
                        return;
                    }
                }
                return; // has key (or unknown hash) -> allow vanilla open
            }

            // ---- Case B: Door is NOT locked: if holding a REGISTERED key, we are registering a lock. ----
            ItemStack held = player.getMainHandItem();
            if (held == null || held.isEmpty()) return;

            if (!(held.getItem() instanceof IronKeyItem)) return;
            if (!IronKeyItem.isRegistered(held)) return;

            // If it’s already locked server-side, client may not know yet; but our payload handler will force-close anyway.
            // Here we are targeting the "fresh door" register action.
            // Cancel vanilla predicted open, send our LockDoorPayload instead.
            ClientDoorOpenBlocker.blockOpenForTicks(doorLong, level.getGameTime(), LOCKSMITH$CLIENT_BLOCK_OPEN_TICKS_AFTER_LOCK_CLICK);

            // Extra safety: visually close immediately (no-op if already closed).
            try {
                DoorLockManager.forceCloseDoorClient(level, doorPos);
            } catch (Throwable t) {
                LOCKSMITH$LOG.debug("[Locksmith][MixinMultiPlayerGameMode] forceCloseDoorClient failed (non-fatal). doorPos={}", doorPos, t);
            }

            try {
                PacketDistributor.sendToServer(new LockDoorPayload(doorLong));
            } catch (Throwable t) {
                LOCKSMITH$LOG.error("[Locksmith][MixinMultiPlayerGameMode] Failed to send LockDoorPayload (non-fatal). doorPos={}", doorPos, t);
                // Even if packet fails, we still cancel predicted open so UX remains consistent.
            }

            // IMPORTANT: cancel the predicted pipeline so the door does not “flash open”.
            cir.setReturnValue(InteractionResult.SUCCESS);

            // Make it obvious in logs that this hook is firing (your earlier attempts produced no client logs).
            if (LOCKSMITH$LOG.isInfoEnabled()) {
                String name = safeName(player);
                LOCKSMITH$LOG.info("[Locksmith] Ate predicted door use to register lock (client). player={} doorPos={}", name, doorPos);
            }

        } catch (Throwable t) {
            LOCKSMITH$LOG.error("[Locksmith][MixinMultiPlayerGameMode] useItemOn intercept failed (non-fatal).", t);
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
