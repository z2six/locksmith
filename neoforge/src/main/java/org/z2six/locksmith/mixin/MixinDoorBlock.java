// MainFile: neoforge/src/main/java/org/z2six/locksmith/mixin/MixinDoorBlock.java
package org.z2six.locksmith.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.lock.DoorLockManager;
import org.z2six.locksmith.network.LockDoorPayload;
import org.z2six.locksmith.render.ClientDoorLockState;
import org.z2six.locksmith.render.ClientDoorOpenBlocker;

@Mixin(DoorBlock.class)
public class MixinDoorBlock {

    private static final Logger LOG = LogUtils.getLogger();

    static {
        try {
            LOG.info("[Locksmith][MixinDoorBlock] LOADED (if you don't see this, the mixin is NOT applying!)");
        } catch (Throwable ignored) {
        }
    }

    // ---- Existing intercepts (kept) ----

    @Inject(method = "use", at = @At("HEAD"), cancellable = true, require = 0)
    private void locksmith$use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit,
                               CallbackInfoReturnable<InteractionResult> cir) {
        locksmith$intercept("use", state, level, pos, player, hand, cir);
    }

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void locksmith$useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
                                          CallbackInfoReturnable<InteractionResult> cir) {
        locksmith$intercept("useWithoutItem", state, level, pos, player, InteractionHand.MAIN_HAND, cir);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, require = 0)
    private void locksmith$useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit,
                                     CallbackInfoReturnable<InteractionResult> cir) {
        locksmith$intercept("useItemOn", state, level, pos, player, hand, cir);
    }

    private void locksmith$intercept(String hook,
                                     BlockState state,
                                     Level level,
                                     BlockPos pos,
                                     Player player,
                                     InteractionHand hand,
                                     CallbackInfoReturnable<InteractionResult> cir) {
        try {
            if (level == null || pos == null || player == null || state == null || cir == null) return;
            if (!(state.getBlock() instanceof DoorBlock)) return;

            BlockPos doorPos = DoorLockManager.normalizeDoorPos(level, pos, state);
            long posLong = doorPos.asLong();

            if (LOG.isDebugEnabled()) {
                LOG.debug("[Locksmith][MixinDoorBlock] fired hook={} side={} pos={} hand={}",
                        hook, (level.isClientSide ? "CLIENT" : "SERVER"), doorPos, hand);
            }

            // Client-side: deny opens for locked doors without key; and for lock-registration,
            // eat interaction + send packet + mark short "block predicted open" window.
            if (level.isClientSide) {
                // If locked and no key -> deny.
                if (ClientDoorLockState.isLocked(posLong)) {
                    String requiredHash = ClientDoorLockState.getRequiredHash(posLong);
                    if (requiredHash != null && !requiredHash.isBlank()) {
                        boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(player, requiredHash);
                        if (!hasKey) {
                            cir.setReturnValue(InteractionResult.FAIL);
                            return;
                        }
                    }
                    return;
                }

                // Not locked: holding registered key -> eat interaction + mark open-block + send payload.
                ItemStack held = player.getMainHandItem();
                if (hand == InteractionHand.MAIN_HAND
                        && held != null
                        && !held.isEmpty()
                        && held.getItem() instanceof IronKeyItem
                        && IronKeyItem.isRegistered(held)) {

                    // Short window: suppress predicted open (real flicker kill is setOpen inject below).
                    ClientDoorOpenBlocker.blockOpenForTicks(posLong, level.getGameTime(), 6);

                    try {
                        PacketDistributor.sendToServer(new LockDoorPayload(posLong));
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("[Locksmith][MixinDoorBlock][Client] Sent LockDoorPayload pos={}", doorPos);
                        }
                    } catch (Throwable t) {
                        LOG.error("[Locksmith][MixinDoorBlock][Client] Failed sending LockDoorPayload (non-fatal).", t);
                    }

                    cir.setReturnValue(InteractionResult.SUCCESS);
                }

                return;
            }

            // Server-side handled in events/payload; keep this path permissive.
        } catch (Throwable t) {
            LOG.error("[Locksmith][MixinDoorBlock] intercept failed (non-fatal).", t);
        }
    }

    // ---- NEW: Cancel the actual client "open" operation ----
    //
    // Important: In your environment (NeoForge 1.21.1 / 21.1.80), DoorBlock has:
    //   setOpen(Entity, Level, BlockState, BlockPos, boolean)
    //
    // There is NOT an overload without Entity. The earlier extra inject caused the mixin
    // to fail applying with "Invalid descriptor".
    //
    // We pin the descriptor to avoid ambiguity and to ensure we only ever target the real method.

    @Inject(
            method = "setOpen(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void locksmith$setOpen(Entity entity, Level level, BlockState state, BlockPos pos, boolean open, CallbackInfo ci) {
        try {
            if (ci == null) return;
            if (level == null || state == null || pos == null) return;

            // Only block predicted opens on the client.
            if (!level.isClientSide) return;
            if (!open) return;
            if (!(state.getBlock() instanceof DoorBlock)) return;

            BlockPos doorLower = DoorLockManager.normalizeDoorPos(level, pos, state);
            long doorLong = doorLower.asLong();
            long now = level.getGameTime();

            // 1) If we're in a "register lock click" window, cancel the open to prevent flicker.
            if (ClientDoorOpenBlocker.shouldBlockOpenNow(doorLong, now)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][MixinDoorBlock] CANCEL predicted open via setOpen at {} (blocker active).", doorLower);
                }
                ci.cancel();
                return;
            }

            // 2) Extra safety: if locked and player entity has no key, cancel the predicted open too.
            if (ClientDoorLockState.isLocked(doorLong)) {
                String requiredHash = ClientDoorLockState.getRequiredHash(doorLong);
                if (requiredHash != null && !requiredHash.isBlank() && entity instanceof Player p) {
                    boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(p, requiredHash);
                    if (!hasKey) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("[Locksmith][MixinDoorBlock] CANCEL predicted open via setOpen at {} (locked + no key).", doorLower);
                        }
                        ci.cancel();
                    }
                }
            }
        } catch (Throwable t) {
            LOG.error("[Locksmith][MixinDoorBlock] setOpen inject failed (non-fatal).", t);
        }
    }
}
