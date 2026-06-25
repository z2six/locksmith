package org.z2six.locksmith.lock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.network.AddChestLockPayload;
import org.z2six.locksmith.network.AddDoorLockPayload;
import org.z2six.locksmith.network.AddGenericLockPayload;
import org.z2six.locksmith.network.HudMessagePayload;
import org.z2six.locksmith.network.RemoveChestLockPayload;
import org.z2six.locksmith.network.RemoveDoorLockPayload;
import org.z2six.locksmith.network.RemoveGenericLockPayload;
import org.z2six.locksmith.render.profile.LockableBlockProfileService;
import org.z2six.locksmith.world.ChestLockSavedData;
import org.z2six.locksmith.world.DoorLockSavedData;
import org.z2six.locksmith.world.GenericLockSavedData;

public final class LockToggleHandler {
    private static final Logger LOG = Constants.LOG;
    private static final int FORCE_CLOSE_AFTER_LOCK_TICKS = 5;

    private LockToggleHandler() {
    }

    public static boolean tryToggle(ServerLevel level, ServerPlayer player, BlockPos clickedPos) {
        try {
            if (level == null || player == null || clickedPos == null) return false;

            BlockState state = level.getBlockState(clickedPos);
            if (state == null || state.isAir()) return false;

            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());

            if (state.getBlock() instanceof DoorBlock && LockableBlockProfileService.isLockableDoor(blockId)) {
                return toggleDoor(level, player, clickedPos, state);
            }
            if (LockableBlockProfileService.isLockableChest(blockId)) {
                return toggleChest(level, player, clickedPos, state);
            }
            if (LockableBlockProfileService.isLockableGeneric(blockId)) {
                return toggleGeneric(level, player, clickedPos);
            }

            return false;
        } catch (Throwable t) {
            LOG.error("[Locksmith][LockToggleHandler] tryToggle failed (non-fatal). pos={}", clickedPos, t);
            return false;
        }
    }

    private static boolean toggleDoor(ServerLevel level, ServerPlayer player, BlockPos clickedPos, BlockState clickedState) {
        BlockPos doorPos = DoorLockManager.normalizeDoorPos(level, clickedPos, clickedState);
        DoorLockSavedData data = DoorLockSavedData.get(level);
        long doorLong = doorPos.asLong();

        if (data.isLocked(doorPos)) {
            String requiredHash = data.getHash(doorPos);
            if (!DoorLockManager.hasMatchingKeyAnywhere(player, requiredHash)) {
                PacketDistributor.sendToPlayer(player, new HudMessagePayload(HudMessagePayload.DOOR_LOCKED_NO_KEY));
                return true;
            }
            if (data.removeLock(doorPos)) {
                for (ServerPlayer other : level.players()) {
                    PacketDistributor.sendToPlayer(other, new RemoveDoorLockPayload(doorLong));
                }
                LOG.info("[Locksmith] Door lock removed at {} by sneak-toggle player={}.", doorPos, player.getName().getString());
            }
            return true;
        }

        String hash = DoorLockManager.getRegisteredKeyHashAnywhere(player);
        if (hash.isBlank()) return false;

        if (data.putLock(doorPos, hash)) {
            DoorLockManager.forceCloseDoor(level, doorPos);
            DoorLockManager.requestForceClose(level, doorPos, FORCE_CLOSE_AFTER_LOCK_TICKS);
            for (ServerPlayer other : level.players()) {
                PacketDistributor.sendToPlayer(other, new AddDoorLockPayload(doorLong, hash));
            }
            PacketDistributor.sendToPlayer(player, new HudMessagePayload(HudMessagePayload.DOOR_LOCK_SUCCESS));
            LOG.info("[Locksmith] Door locked at {} by sneak-toggle player={}.", doorPos, player.getName().getString());
        }
        return true;
    }

    private static boolean toggleChest(ServerLevel level, ServerPlayer player, BlockPos clickedPos, BlockState clickedState) {
        BlockPos chestKeyPos = ChestLockManager.normalizeChestPos(level, clickedPos, clickedState);
        long chestLong = chestKeyPos.asLong();
        ChestLockSavedData data = ChestLockSavedData.get(level);

        if (data.isLockedLong(chestLong)) {
            String requiredHash = data.getHashLong(chestLong);
            if (!DoorLockManager.hasMatchingKeyAnywhere(player, requiredHash)) {
                PacketDistributor.sendToPlayer(player, new HudMessagePayload(HudMessagePayload.CHEST_LOCKED_NO_KEY));
                return true;
            }
            if (data.removeLockLong(chestLong)) {
                for (ServerPlayer other : level.players()) {
                    PacketDistributor.sendToPlayer(other, new RemoveChestLockPayload(chestLong));
                }
                LOG.info("[Locksmith] Chest lock removed at {} by sneak-toggle player={}.", chestKeyPos, player.getName().getString());
            }
            return true;
        }

        String hash = DoorLockManager.getRegisteredKeyHashAnywhere(player);
        if (hash.isBlank()) return false;

        if (data.putLockLong(chestLong, hash)) {
            for (ServerPlayer other : level.players()) {
                PacketDistributor.sendToPlayer(other, new AddChestLockPayload(chestLong, hash));
            }
            PacketDistributor.sendToPlayer(player, new HudMessagePayload(HudMessagePayload.CHEST_LOCK_SUCCESS));
            LOG.info("[Locksmith] Chest locked at {} by sneak-toggle player={}.", chestKeyPos, player.getName().getString());
        }
        return true;
    }

    private static boolean toggleGeneric(ServerLevel level, ServerPlayer player, BlockPos pos) {
        long posLong = pos.asLong();
        GenericLockSavedData data = GenericLockSavedData.get(level);

        if (data.isLockedLong(posLong)) {
            String requiredHash = data.getHashLong(posLong);
            if (!DoorLockManager.hasMatchingKeyAnywhere(player, requiredHash)) {
                PacketDistributor.sendToPlayer(player, new HudMessagePayload(HudMessagePayload.GENERIC_LOCKED_NO_KEY));
                return true;
            }
            if (data.removeLockLong(posLong)) {
                for (ServerPlayer other : level.players()) {
                    PacketDistributor.sendToPlayer(other, new RemoveGenericLockPayload(posLong));
                }
                LOG.info("[Locksmith] Generic lock removed at {} by sneak-toggle player={}.", pos, player.getName().getString());
            }
            return true;
        }

        String hash = DoorLockManager.getRegisteredKeyHashAnywhere(player);
        if (hash.isBlank()) return false;

        if (data.putLockLong(posLong, hash)) {
            for (ServerPlayer other : level.players()) {
                PacketDistributor.sendToPlayer(other, new AddGenericLockPayload(posLong, hash));
            }
            PacketDistributor.sendToPlayer(player, new HudMessagePayload(HudMessagePayload.GENERIC_LOCK_SUCCESS));
            LOG.info("[Locksmith] Generic block locked at {} by sneak-toggle player={}.", pos, player.getName().getString());
        }
        return true;
    }
}
