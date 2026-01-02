// MainFile: neoforge/src/main/java/org/z2six/locksmith/world/DoorLockSavedData.java
package org.z2six.locksmith.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Stores door locks per-dimension.
 *
 * Key: BlockPos.asLong() (normalized to LOWER half position)
 * Value: required hash (hex string)
 */
public class DoorLockSavedData extends SavedData {

    private static final Logger LOG = Constants.LOG;

    private static final String NAME = "locksmith_door_locks";
    private static final String TAG_LOCKS = "Locks";
    private static final String TAG_POS = "Pos";
    private static final String TAG_HASH = "Hash";

    private final Long2ObjectOpenHashMap<String> locks = new Long2ObjectOpenHashMap<>();

    public DoorLockSavedData() {
    }

    public static DoorLockSavedData get(ServerLevel level) {
        try {
            Supplier<DoorLockSavedData> constructor = DoorLockSavedData::new;
            BiFunction<CompoundTag, HolderLookup.Provider, DoorLockSavedData> loader = DoorLockSavedData::load;

            SavedData.Factory<DoorLockSavedData> factory = new SavedData.Factory<>(constructor, loader);

            return level.getDataStorage().computeIfAbsent(factory, NAME);
        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockSavedData] Failed to get SavedData for level {}.",
                    (level == null ? "null" : level.dimension().location()), t);
            return new DoorLockSavedData();
        }
    }

    public static DoorLockSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        DoorLockSavedData data = new DoorLockSavedData();
        try {
            if (tag == null) return data;

            ListTag list = tag.getList(TAG_LOCKS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                long pos = e.getLong(TAG_POS);
                String hash = e.getString(TAG_HASH);
                if (hash != null && !hash.isBlank()) {
                    data.locks.put(pos, hash);
                }
            }

            LOG.info("[Locksmith][DoorLockSavedData] Loaded {} door lock(s).", data.locks.size());
        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockSavedData] load failed (non-fatal).", t);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        try {
            ListTag list = new ListTag();
            for (var en : locks.long2ObjectEntrySet()) {
                long pos = en.getLongKey();
                String hash = en.getValue();
                if (hash == null || hash.isBlank()) continue;

                CompoundTag e = new CompoundTag();
                e.putLong(TAG_POS, pos);
                e.putString(TAG_HASH, hash);
                list.add(e);
            }
            tag.put(TAG_LOCKS, list);
        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockSavedData] save failed (non-fatal).", t);
        }
        return tag;
    }

    public boolean isLocked(BlockPos pos) {
        if (pos == null) return false;
        return locks.containsKey(pos.asLong());
    }

    public boolean isLockedLong(long posLong) {
        return locks.containsKey(posLong);
    }

    public String getHash(BlockPos pos) {
        if (pos == null) return "";
        String v = locks.get(pos.asLong());
        return v == null ? "" : v;
    }

    public String getHashLong(long posLong) {
        String v = locks.get(posLong);
        return v == null ? "" : v;
    }

    public boolean putLock(BlockPos pos, String hash) {
        try {
            if (pos == null) return false;
            if (hash == null || hash.isBlank()) return false;

            long key = pos.asLong();
            String prev = locks.putIfAbsent(key, hash);
            if (prev == null) {
                setDirty();
                LOG.info("[Locksmith][DoorLockSavedData] Added door lock at {} hashLen={}", pos, hash.length());
                return true;
            }
            return false;
        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockSavedData] putLock failed (non-fatal).", t);
            return false;
        }
    }

    public boolean removeLock(BlockPos pos) {
        try {
            if (pos == null) return false;
            long key = pos.asLong();
            return removeLockLong(key);
        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockSavedData] removeLock failed (non-fatal).", t);
            return false;
        }
    }

    public boolean removeLockLong(long posLong) {
        try {
            String removed = locks.remove(posLong);
            if (removed != null) {
                setDirty();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][DoorLockSavedData] Removed door lock at posLong={} hashLen={}", posLong, removed.length());
                } else {
                    LOG.info("[Locksmith][DoorLockSavedData] Removed door lock at posLong={}", posLong);
                }
                return true;
            }
            return false;
        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockSavedData] removeLockLong failed (non-fatal).", t);
            return false;
        }
    }

    /**
     * Hard cleanup pass: removes any lock entries whose block is no longer a door.
     * This guarantees stale locks won't survive weird removals (explosions, /setblock, modded block swaps, etc).
     *
     * @return number of removed entries
     */
    public int cleanupInvalidDoors(ServerLevel level, int maxToCheck) {
        int removedCount = 0;
        try {
            if (level == null) return 0;
            if (locks.isEmpty()) return 0;

            int checked = 0;

            // Iterate over snapshot keys to avoid concurrent modification issues.
            long[] keys = locks.keySet().toLongArray();
            for (long posLong : keys) {
                if (maxToCheck > 0 && checked >= maxToCheck) break;
                checked++;

                BlockPos pos = BlockPos.of(posLong);
                BlockState st = level.getBlockState(pos);
                if (!(st.getBlock() instanceof DoorBlock)) {
                    boolean did = removeLockLong(posLong);
                    if (did) removedCount++;
                }
            }

            if (removedCount > 0) {
                LOG.info("[Locksmith][DoorLockSavedData] Cleanup removed {} stale lock(s). checked={}", removedCount, checked);
            }
        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockSavedData] cleanupInvalidDoors failed (non-fatal).", t);
        }
        return removedCount;
    }

    public Map<Long, String> snapshotLocks() {
        try {
            HashMap<Long, String> m = new HashMap<>();
            for (var en : locks.long2ObjectEntrySet()) {
                m.put(en.getLongKey(), en.getValue());
            }
            return Collections.unmodifiableMap(m);
        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockSavedData] snapshotLocks failed (non-fatal).", t);
            return Collections.emptyMap();
        }
    }
}
