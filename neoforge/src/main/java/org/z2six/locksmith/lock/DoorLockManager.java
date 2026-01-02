// MainFile: neoforge/src/main/java/org/z2six/locksmith/lock/DoorLockManager.java
package org.z2six.locksmith.lock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.registry.ModItems;
import org.z2six.locksmith.world.DoorLockSavedData;

public final class DoorLockManager {

    private static final Logger LOG = Constants.LOG;

    private DoorLockManager() {
        // no-op
    }

    public static boolean isDoor(BlockState state) {
        return state != null && state.getBlock() instanceof DoorBlock;
    }

    /**
     * Normalize door position to LOWER half.
     */
    public static BlockPos normalizeDoorPos(Level level, BlockPos clickedPos, BlockState clickedState) {
        try {
            if (level == null || clickedPos == null || clickedState == null) return clickedPos;
            if (!(clickedState.getBlock() instanceof DoorBlock)) return clickedPos;

            DoubleBlockHalf half = clickedState.getValue(DoorBlock.HALF);
            if (half == DoubleBlockHalf.UPPER) {
                return clickedPos.below();
            }
            return clickedPos;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockManager] normalizeDoorPos failed (non-fatal).", t);
            return clickedPos;
        }
    }

    public static boolean hasMatchingKeyAnywhere(Player player, String requiredHash) {
        try {
            if (player == null) return false;
            if (requiredHash == null || requiredHash.isBlank()) return false;

            // Main inventory + hotbar
            for (ItemStack s : player.getInventory().items) {
                if (stackMatchesHash(s, requiredHash)) return true;
            }

            // Offhand
            for (ItemStack s : player.getInventory().offhand) {
                if (stackMatchesHash(s, requiredHash)) return true;
            }

            // Armor (doesn't matter, but harmless)
            for (ItemStack s : player.getInventory().armor) {
                if (stackMatchesHash(s, requiredHash)) return true;
            }

            return false;
        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockManager] hasMatchingKeyAnywhere failed (non-fatal).", t);
            return false;
        }
    }

    private static boolean stackMatchesHash(ItemStack stack, String requiredHash) {
        try {
            if (stack == null || stack.isEmpty()) return false;
            if (!stack.is(ModItems.KEY_IRON.get())) return false;
            if (!IronKeyItem.isRegistered(stack)) return false;

            String h = IronKeyItem.getHashOrEmpty(stack);
            return requiredHash.equals(h);
        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockManager] stackMatchesHash failed (non-fatal).", t);
            return false;
        }
    }

    public static boolean tryLockDoorWithHeldKey(ServerLevel level, Player player, BlockPos doorLowerPos, ItemStack heldKey) {
        try {
            if (level == null || player == null || doorLowerPos == null) return false;
            if (heldKey == null || heldKey.isEmpty()) return false;
            if (!heldKey.is(ModItems.KEY_IRON.get())) return false;
            if (!IronKeyItem.isRegistered(heldKey)) return false;

            String hash = IronKeyItem.getHashOrEmpty(heldKey);
            if (hash == null || hash.isBlank()) return false;

            DoorLockSavedData data = DoorLockSavedData.get(level);

            // Already locked?
            if (data.isLocked(doorLowerPos)) {
                return false;
            }

            boolean added = data.putLock(doorLowerPos, hash);
            if (added) {
                LOG.info("[Locksmith] Door locked at {} by player={}", doorLowerPos, player.getName().getString());
            }
            return added;
        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockManager] tryLockDoorWithHeldKey failed (non-fatal).", t);
            return false;
        }
    }

    public static String getDoorLockHash(ServerLevel level, BlockPos doorLowerPos) {
        try {
            if (level == null || doorLowerPos == null) return "";
            return DoorLockSavedData.get(level).getHash(doorLowerPos);
        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockManager] getDoorLockHash failed (non-fatal).", t);
            return "";
        }
    }
}
