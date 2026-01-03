// neoforge/src/main/java/org/z2six/locksmith/lock/ChestLockManager.java
package org.z2six.locksmith.lock;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.core.Direction;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.registry.ModItems;
import org.z2six.locksmith.world.ChestLockSavedData;

public final class ChestLockManager {

    private static final Logger LOG = Constants.LOG;

    private ChestLockManager() {
    }

    /**
     * Normalizes chest position:
     * - If this is a double chest, return a canonical "pair key" position
     *   so one lock applies to the whole chest pair.
     * - If not, return the clicked pos.
     *
     * This normalization is best-effort and safe for modded blocks too:
     * if it isn't a ChestBlock or doesn't have expected properties, we just return clickedPos.
     */
    public static BlockPos normalizeChestPos(Level level, BlockPos clickedPos, BlockState state) {
        try {
            if (level == null || clickedPos == null || state == null) return clickedPos;

            if (!(state.getBlock() instanceof ChestBlock)) {
                return clickedPos;
            }

            // Vanilla ChestBlock uses FACING + TYPE.
            Direction facing = Direction.NORTH;
            ChestType chestType = ChestType.SINGLE;

            try {
                DirectionProperty facingProp = ChestBlock.FACING;
                if (state.hasProperty(facingProp)) {
                    facing = state.getValue(facingProp);
                }
            } catch (Throwable ignored) {
            }

            try {
                EnumProperty<ChestType> typeProp = ChestBlock.TYPE;
                if (state.hasProperty(typeProp)) {
                    chestType = state.getValue(typeProp);
                }
            } catch (Throwable ignored) {
            }

            if (chestType == null || chestType == ChestType.SINGLE) {
                return clickedPos;
            }

            // Determine neighbor position for this double chest.
            // For vanilla, the "pair direction" depends on facing and whether this half is LEFT/RIGHT.
            Direction leftDir = facing.getCounterClockWise();
            Direction rightDir = facing.getClockWise();
            BlockPos neighbor = (chestType == ChestType.LEFT) ? clickedPos.relative(rightDir) : clickedPos.relative(leftDir);

            long a = clickedPos.asLong();
            long b = neighbor.asLong();
            return (a <= b) ? clickedPos : neighbor;

        } catch (Throwable t) {
            LOG.warn("[Locksmith][ChestLockManager] normalizeChestPos failed (non-fatal).", t);
            return clickedPos;
        }
    }

    public static boolean tryLockChestWithHeldKey(ServerLevel level, Player player, BlockPos chestKeyPos, ItemStack heldKey) {
        try {
            if (level == null) return false;
            if (player == null || chestKeyPos == null) return false;
            if (heldKey == null || heldKey.isEmpty()) return false;

            if (!heldKey.is(ModItems.KEY_IRON.get())) return false;
            if (!IronKeyItem.isRegistered(heldKey)) return false;

            String hash = IronKeyItem.getHashOrEmpty(heldKey);
            if (hash == null || hash.isBlank()) return false;

            ChestLockSavedData data = ChestLockSavedData.get(level);

            long key = chestKeyPos.asLong();
            if (data.isLockedLong(key)) {
                return false;
            }

            boolean added = data.putLockLong(key, hash);
            if (added) {
                LOG.info("[Locksmith] Chest locked at {} by player={}", chestKeyPos, player.getName().getString());
            }
            return added;

        } catch (Throwable t) {
            LOG.error("[Locksmith][ChestLockManager] tryLockChestWithHeldKey failed (non-fatal).", t);
            return false;
        }
    }

    public static String getChestLockHash(ServerLevel level, BlockPos chestKeyPos) {
        try {
            if (level == null || chestKeyPos == null) return "";
            return ChestLockSavedData.get(level).getHashLong(chestKeyPos.asLong());
        } catch (Throwable t) {
            LOG.error("[Locksmith][ChestLockManager] getChestLockHash failed (non-fatal).", t);
            return "";
        }
    }

    public static String getChestLockHashLong(ServerLevel level, long chestPosLong) {
        try {
            if (level == null) return "";
            return ChestLockSavedData.get(level).getHashLong(chestPosLong);
        } catch (Throwable t) {
            LOG.error("[Locksmith][ChestLockManager] getChestLockHashLong failed (non-fatal).", t);
            return "";
        }
    }
}
