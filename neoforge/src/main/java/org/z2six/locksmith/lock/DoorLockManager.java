// MainFile: neoforge/src/main/java/org/z2six/locksmith/lock/DoorLockManager.java
package org.z2six.locksmith.lock;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.registry.ModItems;
import org.z2six.locksmith.world.DoorLockSavedData;

import java.lang.reflect.Method;
import java.util.Optional;

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

    /**
     * Returns true if the player has a registered iron key whose hash matches requiredHash.
     *
     * Sources checked (in order):
     *  - main inventory items
     *  - offhand
     *  - armor
     *  - Curios "key" slot (optional, reflection-based)
     *
     * IMPORTANT: Curios is NEVER required. If Curios isn't installed, or reflection fails, we just skip it.
     */
    public static boolean hasMatchingKeyAnywhere(Player player, String requiredHash) {
        try {
            if (player == null) return false;
            if (requiredHash == null || requiredHash.isBlank()) return false;

            // Vanilla inventories
            for (ItemStack s : player.getInventory().items) {
                if (stackMatchesHash(s, requiredHash)) return true;
            }
            for (ItemStack s : player.getInventory().offhand) {
                if (stackMatchesHash(s, requiredHash)) return true;
            }
            for (ItemStack s : player.getInventory().armor) {
                if (stackMatchesHash(s, requiredHash)) return true;
            }

            // Optional Curios slot ("key")
            if (hasMatchingKeyInCuriosKeySlot(player, requiredHash)) {
                return true;
            }

            return false;
        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockManager] hasMatchingKeyAnywhere failed (non-fatal).", t);
            return false;
        }
    }

    public static String getRegisteredKeyHashAnywhere(Player player) {
        try {
            ItemStack stack = findRegisteredKeyAnywhere(player);
            if (stack == null || stack.isEmpty()) return "";
            return IronKeyItem.getHashOrEmpty(stack);
        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockManager] getRegisteredKeyHashAnywhere failed (non-fatal).", t);
            return "";
        }
    }

    public static boolean hasRegisteredKeyAnywhere(Player player) {
        return !getRegisteredKeyHashAnywhere(player).isBlank();
    }

    public static ItemStack findRegisteredKeyAnywhere(Player player) {
        try {
            if (player == null) return ItemStack.EMPTY;

            for (ItemStack s : player.getInventory().items) {
                if (stackIsRegisteredKey(s)) return s;
            }
            for (ItemStack s : player.getInventory().offhand) {
                if (stackIsRegisteredKey(s)) return s;
            }
            for (ItemStack s : player.getInventory().armor) {
                if (stackIsRegisteredKey(s)) return s;
            }

            ItemStack curios = findRegisteredKeyInCuriosKeySlot(player);
            return curios == null ? ItemStack.EMPTY : curios;
        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockManager] findRegisteredKeyAnywhere failed (non-fatal).", t);
            return ItemStack.EMPTY;
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

    private static boolean stackIsRegisteredKey(ItemStack stack) {
        try {
            return stack != null
                    && !stack.isEmpty()
                    && stack.is(ModItems.KEY_IRON.get())
                    && IronKeyItem.isRegistered(stack);
        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockManager] stackIsRegisteredKey failed (non-fatal).", t);
            return false;
        }
    }

    /**
     * Finds the "double door mate" for a LOCKED door, by checking the block on the opposite side of the hinge.
     *
     * Requirements to be considered a mate:
     *  - neighbor is a door block
     *  - same FACING
     *  - opposite HINGE side
     *  - neighbor door's LOWER block pos is computed/normalized
     *  - neighbor door is ALSO locked by Locksmith (so we do not affect vanilla/unlocked doors)
     *
     * Returns null if no valid mate exists.
     *
     * This is deliberately limited to a single neighbor lookup: it will never chain beyond 2 doors.
     */
    public static BlockPos findLockedDoubleDoorMate(ServerLevel level, BlockPos doorLowerPos) {
        try {
            if (level == null || doorLowerPos == null) return null;

            BlockState lower = level.getBlockState(doorLowerPos);
            if (!(lower.getBlock() instanceof DoorBlock)) return null;

            // Must be locked itself (paranoia)
            DoorLockSavedData data = DoorLockSavedData.get(level);
            if (!data.isLocked(doorLowerPos)) return null;

            if (!lower.hasProperty(DoorBlock.FACING) || !lower.hasProperty(DoorBlock.HINGE) || !lower.hasProperty(DoorBlock.HALF)) {
                return null;
            }

            // Ensure we are operating on LOWER half pos
            try {
                if (lower.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
                    doorLowerPos = doorLowerPos.below();
                    lower = level.getBlockState(doorLowerPos);
                    if (!(lower.getBlock() instanceof DoorBlock)) return null;
                }
            } catch (Throwable ignored) {
            }

            Direction facing = Direction.NORTH;
            DoorHingeSide hinge = DoorHingeSide.LEFT;
            try {
                facing = lower.getValue(DoorBlock.FACING);
            } catch (Throwable ignored) {
            }
            try {
                hinge = lower.getValue(DoorBlock.HINGE);
            } catch (Throwable ignored) {
            }

            // "Opposite side of hinge": if hinge is LEFT, mate is on RIGHT side, and vice versa.
            Direction sideDir = (hinge == DoorHingeSide.LEFT) ? facing.getClockWise() : facing.getCounterClockWise();
            BlockPos neighborPos = doorLowerPos.relative(sideDir);

            BlockState neighborState = level.getBlockState(neighborPos);
            if (!(neighborState.getBlock() instanceof DoorBlock)) return null;

            // Normalize neighbor pos to LOWER half
            BlockPos neighborLower = neighborPos;
            try {
                if (neighborState.hasProperty(DoorBlock.HALF) && neighborState.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
                    neighborLower = neighborPos.below();
                    neighborState = level.getBlockState(neighborLower);
                }
            } catch (Throwable ignored) {
            }
            if (!(neighborState.getBlock() instanceof DoorBlock)) return null;

            // Must match facing
            try {
                if (neighborState.hasProperty(DoorBlock.FACING)) {
                    Direction nf = neighborState.getValue(DoorBlock.FACING);
                    if (nf != facing) return null;
                } else {
                    return null;
                }
            } catch (Throwable t) {
                return null;
            }

            // Must have opposite hinge
            try {
                if (neighborState.hasProperty(DoorBlock.HINGE)) {
                    DoorHingeSide nh = neighborState.getValue(DoorBlock.HINGE);
                    boolean opposite = (hinge == DoorHingeSide.LEFT && nh == DoorHingeSide.RIGHT)
                            || (hinge == DoorHingeSide.RIGHT && nh == DoorHingeSide.LEFT);
                    if (!opposite) return null;
                } else {
                    return null;
                }
            } catch (Throwable t) {
                return null;
            }

            // Neighbor must ALSO be locked by Locksmith
            if (!data.isLocked(neighborLower)) {
                return null;
            }

            return neighborLower;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockManager] findLockedDoubleDoorMate failed (non-fatal).", t);
            return null;
        }
    }

    /**
     * Opens/closes a door at its LOWER position using DoorBlock#setOpen if available.
     * Safe, no crash, logs on failure. Works server-side.
     */
    public static void setDoorOpen(ServerLevel level, BlockPos doorLowerPos, Player actor, boolean open) {
        try {
            if (level == null || doorLowerPos == null) return;

            BlockState st = level.getBlockState(doorLowerPos);
            if (!(st.getBlock() instanceof DoorBlock door)) return;

            // Ensure we have LOWER pos; if upper given, shift down.
            try {
                if (st.hasProperty(DoorBlock.HALF) && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
                    doorLowerPos = doorLowerPos.below();
                    st = level.getBlockState(doorLowerPos);
                    if (!(st.getBlock() instanceof DoorBlock door2)) return;
                    door = door2;
                }
            } catch (Throwable ignored) {
            }

            // If already desired state, do nothing.
            try {
                if (st.hasProperty(DoorBlock.OPEN)) {
                    boolean current = st.getValue(DoorBlock.OPEN);
                    if (current == open) return;
                }
            } catch (Throwable ignored) {
            }

            // DoorBlock#setOpen triggers correct sounds + neighbor updates.
            try {
                door.setOpen(actor, level, st, doorLowerPos, open);
            } catch (Throwable t) {
                // Fallback: direct blockstate toggles (non-ideal but better than failing).
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][DoorLockManager] setDoorOpen: DoorBlock#setOpen failed, falling back to state set. open={}", open, t);
                }
                try {
                    if (st.hasProperty(DoorBlock.OPEN)) {
                        BlockState newLower = st.setValue(DoorBlock.OPEN, open);
                        level.setBlock(doorLowerPos, newLower, 3);

                        BlockPos upperPos = doorLowerPos.above();
                        BlockState upper = level.getBlockState(upperPos);
                        if (upper.getBlock() instanceof DoorBlock && upper.hasProperty(DoorBlock.OPEN)) {
                            BlockState newUpper = upper.setValue(DoorBlock.OPEN, open);
                            level.setBlock(upperPos, newUpper, 3);
                        }
                    }
                } catch (Throwable t2) {
                    LOG.warn("[Locksmith][DoorLockManager] setDoorOpen fallback failed (non-fatal).", t2);
                }
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockManager] setDoorOpen failed (non-fatal). open={}", open, t);
        }
    }

    // ---------------- Curios support (unchanged logic, kept here) ----------------

    private static boolean hasMatchingKeyInCuriosKeySlot(Player player, String requiredHash) {
        try {
            if (player == null) return false;
            if (requiredHash == null || requiredHash.isBlank()) return false;

            boolean curiosLoaded;
            try {
                curiosLoaded = ModList.get().isLoaded("curios");
            } catch (Throwable t) {
                curiosLoaded = false;
            }
            if (!curiosLoaded) return false;

            Optional<Object> dynamicOpt = getCuriosDynamicHandler(player, "key");
            if (dynamicOpt.isEmpty()) return false;

            Object dynamic = dynamicOpt.get();
            if (dynamic == null) return false;

            Method getSlots = dynamic.getClass().getMethod("getSlots");
            int slots = (int) getSlots.invoke(dynamic);
            if (slots <= 0) return false;

            Method getStackInSlot = dynamic.getClass().getMethod("getStackInSlot", int.class);

            for (int i = 0; i < slots; i++) {
                Object stackObj = getStackInSlot.invoke(dynamic, i);
                if (!(stackObj instanceof ItemStack stack)) continue;
                if (stack.isEmpty()) continue;

                if (stackMatchesHash(stack, requiredHash)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[Locksmith][Curios] Found matching key hash in Curios 'key' slot for player={} slotIndex={}",
                                player.getName().getString(), i);
                    }
                    return true;
                }
            }

            return false;
        } catch (Throwable t) {
            LOG.debug("[Locksmith][DoorLockManager] hasMatchingKeyInCuriosKeySlot failed (non-fatal).", t);
            return false;
        }
    }

    private static ItemStack findRegisteredKeyInCuriosKeySlot(Player player) {
        try {
            if (player == null) return ItemStack.EMPTY;

            boolean curiosLoaded;
            try {
                curiosLoaded = ModList.get().isLoaded("curios");
            } catch (Throwable t) {
                curiosLoaded = false;
            }
            if (!curiosLoaded) return ItemStack.EMPTY;

            Optional<Object> dynamicOpt = getCuriosDynamicHandler(player, "key");
            if (dynamicOpt.isEmpty()) return ItemStack.EMPTY;

            Object dynamic = dynamicOpt.get();
            if (dynamic == null) return ItemStack.EMPTY;

            Method getSlots = dynamic.getClass().getMethod("getSlots");
            int slots = (int) getSlots.invoke(dynamic);
            if (slots <= 0) return ItemStack.EMPTY;

            Method getStackInSlot = dynamic.getClass().getMethod("getStackInSlot", int.class);
            for (int i = 0; i < slots; i++) {
                Object stackObj = getStackInSlot.invoke(dynamic, i);
                if (stackObj instanceof ItemStack stack && stackIsRegisteredKey(stack)) {
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        } catch (Throwable t) {
            LOG.debug("[Locksmith][DoorLockManager] findRegisteredKeyInCuriosKeySlot failed (non-fatal).", t);
            return ItemStack.EMPTY;
        }
    }

    private static Optional<Object> getCuriosDynamicHandler(LivingEntity entity, String slotType) {
        try {
            if (entity == null) return Optional.empty();
            if (slotType == null || slotType.isBlank()) return Optional.empty();

            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getInv = curiosApi.getMethod("getCuriosInventory", LivingEntity.class);
            Object invOptObj = getInv.invoke(null, entity);

            if (!(invOptObj instanceof Optional<?> invOpt) || invOpt.isEmpty()) {
                return Optional.empty();
            }

            Object curiosHandler = invOpt.get();
            if (curiosHandler == null) return Optional.empty();

            Method getStacksHandler = curiosHandler.getClass().getMethod("getStacksHandler", String.class);
            Object stacksOptObj = getStacksHandler.invoke(curiosHandler, slotType);

            if (!(stacksOptObj instanceof Optional<?> stacksOpt) || stacksOpt.isEmpty()) {
                return Optional.empty();
            }

            Object stacksHandler = stacksOpt.get();
            if (stacksHandler == null) return Optional.empty();

            Method getStacks = stacksHandler.getClass().getMethod("getStacks");
            Object dynamic = getStacks.invoke(stacksHandler);

            return Optional.ofNullable(dynamic);
        } catch (Throwable t) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("[Locksmith][DoorLockManager] getCuriosDynamicHandler failed (non-fatal). slotType={}", slotType, t);
            }
            return Optional.empty();
        }
    }

    // ---------------- Existing locking + force-close logic (unchanged) ----------------

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
            if (!level.isClientSide) return;
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
