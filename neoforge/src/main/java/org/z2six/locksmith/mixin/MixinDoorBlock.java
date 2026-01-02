// MainFile: neoforge/src/main/java/org/z2six/locksmith/mixin/MixinDoorBlock.java
package org.z2six.locksmith.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.lock.DoorLockManager;
import org.z2six.locksmith.network.LockDoorPayload;
import org.z2six.locksmith.render.ClientDoorLockState;
import org.z2six.locksmith.world.DoorLockSavedData;

@Mixin(DoorBlock.class)
public class MixinDoorBlock {

    private static final Logger LOG = LogUtils.getLogger();

    static {
        try {
            LOG.info("[Locksmith][MixinDoorBlock] LOADED");
        } catch (Throwable ignored) {
        }
    }

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
            if (level == null || pos == null || player == null || state == null) return;
            if (!(state.getBlock() instanceof DoorBlock)) return;

            BlockPos doorPos = DoorLockManager.normalizeDoorPos(level, pos, state);
            long posLong = doorPos.asLong();

            if (LOG.isDebugEnabled()) {
                LOG.debug("[Locksmith][MixinDoorBlock] fired hook={} side={} pos={} hand={}",
                        hook, (level.isClientSide ? "CLIENT" : "SERVER"), doorPos, hand);
            }

            // ---------------- CLIENT ----------------
            if (level.isClientSide) {
                // If already locked and player lacks key -> deny.
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

                // Not locked: holding registered key => eat interaction + send payload, never toggle door.
                ItemStack held = player.getMainHandItem();
                if (hand == InteractionHand.MAIN_HAND
                        && held != null
                        && !held.isEmpty()
                        && held.getItem() instanceof IronKeyItem
                        && IronKeyItem.isRegistered(held)) {

                    try {
                        PacketDistributor.sendToServer(new LockDoorPayload(posLong));
                    } catch (Throwable t) {
                        LOG.error("[Locksmith][MixinDoorBlock][Client] Failed sending LockDoorPayload (non-fatal).", t);
                    }

                    cir.setReturnValue(InteractionResult.SUCCESS);
                }

                return;
            }

            // ---------------- SERVER ----------------
            if (!(level instanceof ServerLevel sLevel)) return;

            DoorLockSavedData data = DoorLockSavedData.get(sLevel);

            // If locked and player lacks key -> deny.
            if (data.isLocked(doorPos)) {
                String requiredHash = data.getHash(doorPos);
                if (requiredHash != null && !requiredHash.isBlank()) {
                    boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(player, requiredHash);
                    if (!hasKey) {
                        cir.setReturnValue(InteractionResult.FAIL);
                        return;
                    }
                }
                return;
            }

            // Not locked: holding registered key => eat interaction, do not toggle door.
            ItemStack held = player.getMainHandItem();
            if (hand == InteractionHand.MAIN_HAND
                    && held != null
                    && !held.isEmpty()
                    && held.getItem() instanceof IronKeyItem
                    && IronKeyItem.isRegistered(held)) {

                boolean added = DoorLockManager.tryLockDoorWithHeldKey(sLevel, player, doorPos, held);

                // Slam shut now and for a few ticks.
                DoorLockManager.forceCloseDoor(sLevel, doorPos);
                DoorLockManager.requestForceClose(sLevel, doorPos, 5);

                if (added && LOG.isInfoEnabled()) {
                    LOG.info("[Locksmith][MixinDoorBlock] Locked door at {} via mixin path for player={}",
                            doorPos, player.getName().getString());
                }

                cir.setReturnValue(InteractionResult.SUCCESS);
            }

        } catch (Throwable t) {
            LOG.error("[Locksmith][MixinDoorBlock] intercept failed (non-fatal).", t);
        }
    }
}
