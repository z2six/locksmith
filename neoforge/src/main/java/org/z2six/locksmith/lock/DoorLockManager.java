// MainFile: neoforge/src/main/java/org/z2six/locksmith/lock/DoorLockManager.java
package org.z2six.locksmith.lock;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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

    /**
     * After placing a lock we keep slamming the door shut for a few ticks.
     * This prevents any "vanilla toggles after our close" ordering race.
     *
     * Key: doorLowerPos.asLong()
     * Value: remaining ticks to enforce close.
     */
    private static final Long2IntOpenHashMap FORCE_CLOSE_TICKS = new Long2IntOpenHashMap();

    static {
        try {
            FORCE_CLOSE_TICKS.defaultReturnValue(0);
        } catch (Throwable ignored) {
        }
    }

    private DoorLockManager() {
    }

    public static boolean isDoor(BlockState state) {
        return state != null && state.getBlock() instanceof DoorBlock;
    }

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

            for (ItemStack s : player.getInventory().items) {
                if (stackMatchesHash(s, requiredHash)) return true;
            }
            for (ItemStack s : player.getInventory().offhand) {
                if (stackMatchesHash(s, requiredHash)) return true;
            }
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

    public static void forceCloseDoor(ServerLevel level, BlockPos doorLowerPos) {
        try {
            if (level == null || doorLowerPos == null) return;

            BlockState lower = level.getBlockState(doorLowerPos);
            if (!(lower.getBlock() instanceof DoorBlock)) return;

            if (lower.hasProperty(DoorBlock.OPEN) && lower.getValue(DoorBlock.OPEN)) {
                BlockState closedLower = lower.setValue(DoorBlock.OPEN, false);
                level.setBlock(doorLowerPos, closedLower, 3);
            }

            BlockPos upperPos = doorLowerPos.above();
            BlockState upper = level.getBlockState(upperPos);
            if (upper.getBlock() instanceof DoorBlock) {
                if (upper.hasProperty(DoorBlock.OPEN) && upper.getValue(DoorBlock.OPEN)) {
                    BlockState closedUpper = upper.setValue(DoorBlock.OPEN, false);
                    level.setBlock(upperPos, closedUpper, 3);
                }
            }

            if (LOG.isDebugEnabled() && (level.getGameTime() % 20 == 0)) {
                Direction facing = lower.hasProperty(DoorBlock.FACING) ? lower.getValue(DoorBlock.FACING) : Direction.NORTH;
                LOG.debug("[Locksmith][DoorLockManager] forceCloseDoor applied at {} facing={}", doorLowerPos, facing);
            }

        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockManager] forceCloseDoor failed (non-fatal).", t);
        }
    }

    /**
     * Request that a door be forcibly closed for N ticks.
     * This is our "no matter what, door ends closed" safety net.
     */
    public static void requestForceClose(ServerLevel level, BlockPos doorLowerPos, int ticks) {
        try {
            if (level == null || doorLowerPos == null) return;
            if (ticks <= 0) ticks = 1;

            long key = doorLowerPos.asLong();

            synchronized (FORCE_CLOSE_TICKS) {
                int prev = FORCE_CLOSE_TICKS.get(key);
                int next = Math.max(prev, ticks);
                FORCE_CLOSE_TICKS.put(key, next);
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("[Locksmith][DoorLockManager] requestForceClose pos={} ticks={}", doorLowerPos, ticks);
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockManager] requestForceClose failed (non-fatal).", t);
        }
    }

    /**
     * Called from server tick. Slams shut any queued doors and decrements.
     */
    public static void tickForceClose(ServerLevel level, int maxPerTick) {
        try {
            if (level == null) return;

            int processed = 0;

            long[] keysSnapshot;
            synchronized (FORCE_CLOSE_TICKS) {
                keysSnapshot = FORCE_CLOSE_TICKS.keySet().toLongArray();
            }

            for (long posLong : keysSnapshot) {
                if (maxPerTick > 0 && processed >= maxPerTick) break;

                int remaining;
                synchronized (FORCE_CLOSE_TICKS) {
                    remaining = FORCE_CLOSE_TICKS.get(posLong);
                    if (remaining <= 0) {
                        FORCE_CLOSE_TICKS.remove(posLong);
                        continue;
                    }
                }

                BlockPos pos = BlockPos.of(posLong);
                forceCloseDoor(level, pos);

                synchronized (FORCE_CLOSE_TICKS) {
                    int now = FORCE_CLOSE_TICKS.get(posLong);
                    now--;
                    if (now <= 0) {
                        FORCE_CLOSE_TICKS.remove(posLong);
                    } else {
                        FORCE_CLOSE_TICKS.put(posLong, now);
                    }
                }

                processed++;
            }

            if (processed > 0 && LOG.isDebugEnabled() && (level.getGameTime() % 40 == 0)) {
                LOG.debug("[Locksmith][DoorLockManager] tickForceClose processed={} remaining={}",
                        processed, FORCE_CLOSE_TICKS.size());
            }

        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockManager] tickForceClose failed (non-fatal).", t);
        }
    }
}
