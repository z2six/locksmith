// MainFile: neoforge/src/main/java/org/z2six/locksmith/lock/DoorLockManager.java
package org.z2six.locksmith.lock;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.slf4j.Logger;
import org.z2six.locksmith.registry.ModItems;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.world.DoorLockSavedData;

public final class DoorLockManager {

    private static final Logger LOG = LogUtils.getLogger();
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

    public static boolean tryLockDoorWithHeldKey(ServerLevelAccessor level, Player player, BlockPos doorLowerPos, ItemStack heldKey) {
        try {
            if (!(level instanceof net.minecraft.server.level.ServerLevel sLevel)) return false;
            if (player == null || doorLowerPos == null) return false;
            if (heldKey == null || heldKey.isEmpty()) return false;
            if (!heldKey.is(ModItems.KEY_IRON.get())) return false;
            if (!IronKeyItem.isRegistered(heldKey)) return false;

            String hash = IronKeyItem.getHashOrEmpty(heldKey);
            if (hash == null || hash.isBlank()) return false;

            DoorLockSavedData data = DoorLockSavedData.get(sLevel);

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

    public static String getDoorLockHash(net.minecraft.server.level.ServerLevel level, BlockPos doorLowerPos) {
        try {
            if (level == null || doorLowerPos == null) return "";
            return DoorLockSavedData.get(level).getHash(doorLowerPos);
        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockManager] getDoorLockHash failed (non-fatal).", t);
            return "";
        }
    }

    public static void forceCloseDoor(net.minecraft.server.level.ServerLevel level, BlockPos doorLowerPos) {
        try {
            if (level == null || doorLowerPos == null) return;
            forceCloseDoorAnyLevel(level, doorLowerPos, "[Server]");
        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockManager] forceCloseDoor failed (non-fatal).", t);
        }
    }

    public static void forceCloseDoorClient(Level level, BlockPos doorLowerPos) {
        try {
            if (level == null || doorLowerPos == null) return;
            if (!level.isClientSide) return; // paranoia: never touch server world here.
            forceCloseDoorAnyLevel(level, doorLowerPos, "[ClientVisual]");
        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockManager] forceCloseDoorClient failed (non-fatal).", t);
        }
    }

    private static void forceCloseDoorAnyLevel(Level level, BlockPos doorLowerPos, String tag) {
        try {
            BlockState lower = level.getBlockState(doorLowerPos);
            if (!(lower.getBlock() instanceof DoorBlock)) return;

            boolean changed = false;

            if (lower.hasProperty(DoorBlock.OPEN) && lower.getValue(DoorBlock.OPEN)) {
                BlockState closedLower = lower.setValue(DoorBlock.OPEN, false);
                // flag 3: update + render. Works fine for client visual.
                level.setBlock(doorLowerPos, closedLower, 3);
                changed = true;
            }

            BlockPos upperPos = doorLowerPos.above();
            BlockState upper = level.getBlockState(upperPos);
            if (upper.getBlock() instanceof DoorBlock) {
                if (upper.hasProperty(DoorBlock.OPEN) && upper.getValue(DoorBlock.OPEN)) {
                    BlockState closedUpper = upper.setValue(DoorBlock.OPEN, false);
                    level.setBlock(upperPos, closedUpper, 3);
                    changed = true;
                }
            }

            // Only occasionally log to avoid spam, but enough to debug.
            if (changed && LOG.isDebugEnabled() && (level.getGameTime() % 5 == 0)) {
                Direction facing = lower.hasProperty(DoorBlock.FACING) ? lower.getValue(DoorBlock.FACING) : Direction.NORTH;
                LOG.debug("[Locksmith][DoorLockManager] {} forceCloseDoor applied at {} facing={}", tag, doorLowerPos, facing);
            }

        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockManager] forceCloseDoorAnyLevel failed (non-fatal). tag={}", tag, t);
        }
    }

    public static void requestForceClose(net.minecraft.server.level.ServerLevel level, BlockPos doorLowerPos, int ticks) {
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

    public static void tickForceClose(net.minecraft.server.level.ServerLevel level, int maxPerTick) {
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
