// neoforge/src/main/java/org/z2six/locksmith/mixin/MixinMultiPlayerGameMode.java
package org.z2six.locksmith.mixin;

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

import com.mojang.logging.LogUtils;

@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {

    @Unique
    private static final Logger LOCKSMITH$LOG = LogUtils.getLogger();

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

            // DOOR path (existing behavior) -------------------------------------------------
            if (clickedState.getBlock() instanceof DoorBlock) {
                BlockPos doorPos = DoorLockManager.normalizeDoorPos(level, clickedPos, clickedState);
                long doorLong = doorPos.asLong();

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
                    return;
                }

                ItemStack held = player.getMainHandItem();
                if (held == null || held.isEmpty()) return;

                if (!(held.getItem() instanceof IronKeyItem)) return;
                if (!IronKeyItem.isRegistered(held)) return;

                ClientDoorOpenBlocker.blockOpenForTicks(doorLong, level.getGameTime(), LOCKSMITH$CLIENT_BLOCK_OPEN_TICKS_AFTER_LOCK_CLICK);

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

                cir.setReturnValue(InteractionResult.SUCCESS);

                if (LOCKSMITH$LOG.isInfoEnabled()) {
                    String name = safeName(player);
                    LOCKSMITH$LOG.info("[Locksmith] Ate predicted door use to register lock (client). player={} doorPos={}", name, doorPos);
                }
                return;
            }

            // CHEST path (new) --------------------------------------------------------------
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

            if (ClientChestLockState.isLocked(chestKeyLong)) {
                String requiredHash = ClientChestLockState.getRequiredHash(chestKeyLong);
                if (requiredHash != null && !requiredHash.isBlank()) {
                    boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(player, requiredHash);
                    if (!hasKey) {
                        cir.setReturnValue(InteractionResult.FAIL);
                        if (LOCKSMITH$LOG.isDebugEnabled()) {
                            LOCKSMITH$LOG.debug("[Locksmith][MixinMultiPlayerGameMode] Denied locked chest predicted-use (no key). chestKeyPos={}", chestKeyPos);
                        }
                        return;
                    }
                }
                return;
            }

            ItemStack held = player.getMainHandItem();
            if (held == null || held.isEmpty()) return;

            if (!(held.getItem() instanceof IronKeyItem)) return;
            if (!IronKeyItem.isRegistered(held)) return;

            ClientChestOpenBlocker.blockOpenForTicks(chestKeyLong, level.getGameTime(), LOCKSMITH$CLIENT_BLOCK_OPEN_TICKS_AFTER_LOCK_CLICK);

            try {
                PacketDistributor.sendToServer(new LockChestPayload(chestKeyLong));
            } catch (Throwable t) {
                LOCKSMITH$LOG.error("[Locksmith][MixinMultiPlayerGameMode] Failed to send LockChestPayload (non-fatal). chestKeyPos={}", chestKeyPos, t);
            }

            cir.setReturnValue(InteractionResult.SUCCESS);

            if (LOCKSMITH$LOG.isInfoEnabled()) {
                String name = safeName(player);
                LOCKSMITH$LOG.info("[Locksmith] Ate predicted chest use to register lock (client). player={} chestKeyPos={}", name, chestKeyPos);
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
