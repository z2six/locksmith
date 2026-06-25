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
import org.z2six.locksmith.client.ClientKeyMappings;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.lock.ChestLockManager;
import org.z2six.locksmith.lock.DoorLockManager;
import org.z2six.locksmith.network.LockChestPayload;
import org.z2six.locksmith.network.LockDoorPayload;
import org.z2six.locksmith.network.ToggleLockPayload;
import org.z2six.locksmith.render.ClientChestLockState;
import org.z2six.locksmith.render.ClientChestOpenBlocker;
import org.z2six.locksmith.render.ClientDoorLockState;
import org.z2six.locksmith.render.ClientDoorOpenBlocker;
import org.z2six.locksmith.render.ClientGenericLockPulse;
import org.z2six.locksmith.render.ClientGenericLockState;
import org.z2six.locksmith.render.profile.ClientLockRenderProfiles;
import org.z2six.locksmith.render.profile.LockRenderProfile;
import org.z2six.locksmith.render.profile.LockTargetType;
import org.z2six.locksmith.network.LockGenericPayload;

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
    private static volatile long LOCKSMITH$LAST_DENY_GENERIC_TICK = -999999L;

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

            if (player.isShiftKeyDown()
                    && ClientKeyMappings.isSneakLockingEnabled()
                    && locksmith$handleSneakToggle(player, level, clickedPos, clickedState, cir)) {
                return;
            }

            // DOOR path --------------------------------------------------------------------
            if (clickedState.getBlock() instanceof DoorBlock) {
                ResourceLocation blockId = null;
                try {
                    blockId = BuiltInRegistries.BLOCK.getKey(clickedState.getBlock());
                } catch (Throwable ignored) {
                }
                LockRenderProfile doorProfile = blockId == null ? null : ClientLockRenderProfiles.get(blockId);
                if (doorProfile == null || !doorProfile.isValid() || doorProfile.type != LockTargetType.DOOR) {
                    return;
                }

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

            LockRenderProfile prof = (blockId == null) ? null : ClientLockRenderProfiles.get(blockId);
            if (prof == null || !prof.isValid()) {
                return;
            }

            if (prof.type == LockTargetType.GENERIC) {
                long genericLong = clickedPos.asLong();

                if (ClientGenericLockState.isLocked(genericLong)) {
                    String requiredHash = ClientGenericLockState.getRequiredHash(genericLong);
                    if (requiredHash != null && !requiredHash.isBlank()) {
                        boolean hasKey = DoorLockManager.hasMatchingKeyAnywhere(player, requiredHash);
                        if (!hasKey) {
                            cir.setReturnValue(InteractionResult.FAIL);
                            locksmith$maybeShowGenericDenied(level);
                            if (LOCKSMITH$LOG.isInfoEnabled()) {
                                LOCKSMITH$LOG.info("[Locksmith][MixinMultiPlayerGameMode] Denied locked generic predicted-use (no key). pos={}", clickedPos);
                            }
                            return;
                        }
                    }
                    ClientGenericLockPulse.trigger(genericLong, level.getGameTime());
                    return;
                }

                ItemStack held = player.getMainHandItem();
                if (held == null || held.isEmpty()) return;
                if (!(held.getItem() instanceof IronKeyItem)) return;
                if (!IronKeyItem.isRegistered(held)) return;

                try {
                    PacketDistributor.sendToServer(new LockGenericPayload(genericLong));
                } catch (Throwable t) {
                    LOCKSMITH$LOG.error("[Locksmith][MixinMultiPlayerGameMode] Failed to send LockGenericPayload (non-fatal). pos={}", clickedPos, t);
                }

                cir.setReturnValue(InteractionResult.SUCCESS);

                if (LOCKSMITH$LOG.isInfoEnabled()) {
                    String name = safeName(player);
                    LOCKSMITH$LOG.info("[Locksmith] Ate predicted generic use to register lock (client). player={} pos={}", name, clickedPos);
                }
                return;
            }

            if (prof.type != LockTargetType.CHEST) {
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
    private static boolean locksmith$handleSneakToggle(
            LocalPlayer player,
            Level level,
            BlockPos clickedPos,
            BlockState clickedState,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        try {
            if (player == null || level == null || clickedPos == null || clickedState == null) return false;

            ResourceLocation blockId = null;
            try {
                blockId = BuiltInRegistries.BLOCK.getKey(clickedState.getBlock());
            } catch (Throwable ignored) {
            }

            long clientLockLong = clickedPos.asLong();
            byte deniedKind = 0;
            boolean configured = false;

            if (clickedState.getBlock() instanceof DoorBlock) {
                LockRenderProfile doorProfile = blockId == null ? null : ClientLockRenderProfiles.get(blockId);
                if (doorProfile == null || !doorProfile.isValid() || doorProfile.type != LockTargetType.DOOR) {
                    return false;
                }
                configured = true;
                clientLockLong = DoorLockManager.normalizeDoorPos(level, clickedPos, clickedState).asLong();
                deniedKind = 1;
            } else {
                LockRenderProfile profile = blockId == null ? null : ClientLockRenderProfiles.get(blockId);
                if (profile == null || !profile.isValid()) {
                    return false;
                }
                if (profile.type == LockTargetType.CHEST) {
                    configured = true;
                    clientLockLong = ChestLockManager.normalizeChestPos(level, clickedPos, clickedState).asLong();
                    deniedKind = 2;
                } else if (profile.type == LockTargetType.GENERIC) {
                    configured = true;
                    clientLockLong = clickedPos.asLong();
                    deniedKind = 3;
                } else {
                    return false;
                }
            }

            if (!configured) return false;

            boolean locked = switch (deniedKind) {
                case 1 -> ClientDoorLockState.isLocked(clientLockLong);
                case 2 -> ClientChestLockState.isLocked(clientLockLong);
                case 3 -> ClientGenericLockState.isLocked(clientLockLong);
                default -> false;
            };

            if (locked) {
                String requiredHash = switch (deniedKind) {
                    case 1 -> ClientDoorLockState.getRequiredHash(clientLockLong);
                    case 2 -> ClientChestLockState.getRequiredHash(clientLockLong);
                    case 3 -> ClientGenericLockState.getRequiredHash(clientLockLong);
                    default -> "";
                };
                if (!DoorLockManager.hasMatchingKeyAnywhere(player, requiredHash)) {
                    cir.setReturnValue(InteractionResult.FAIL);
                    if (deniedKind == 1) locksmith$maybeShowDoorDenied(level);
                    else if (deniedKind == 2) locksmith$maybeShowChestDenied(level);
                    else locksmith$maybeShowGenericDenied(level);
                    return true;
                }
            } else if (!DoorLockManager.hasRegisteredKeyAnywhere(player)) {
                return false;
            }

            try {
                PacketDistributor.sendToServer(new ToggleLockPayload(clickedPos.asLong()));
            } catch (Throwable t) {
                LOCKSMITH$LOG.error("[Locksmith][MixinMultiPlayerGameMode] Failed to send ToggleLockPayload (non-fatal). pos={}", clickedPos, t);
            }

            cir.setReturnValue(InteractionResult.SUCCESS);
            return true;
        } catch (Throwable t) {
            LOCKSMITH$LOG.error("[Locksmith][MixinMultiPlayerGameMode] sneak-toggle intercept failed (non-fatal).", t);
            return false;
        }
    }

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
    private static void locksmith$maybeShowGenericDenied(Level level) {
        try {
            if (level == null) return;
            long now = level.getGameTime();
            if (now - LOCKSMITH$LAST_DENY_GENERIC_TICK < LOCKSMITH$MSG_THROTTLE_TICKS) return;
            LOCKSMITH$LAST_DENY_GENERIC_TICK = now;

            ClientHudMessages.showGenericLockedNoKey();

            if (LOCKSMITH$LOG.isInfoEnabled()) {
                LOCKSMITH$LOG.info("[Locksmith][MixinMultiPlayerGameMode] Triggered HUD deny message (generic_locked_no_key). tick={}", now);
            }
        } catch (Throwable t) {
            LOCKSMITH$LOG.warn("[Locksmith][MixinMultiPlayerGameMode] locksmith$maybeShowGenericDenied failed (non-fatal).", t);
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
