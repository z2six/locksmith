// neoforge/src/main/java/org/z2six/locksmith/world/ChestLockSavedData.java
package org.z2six.locksmith.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class ChestLockSavedData extends SavedData {

    private static final Logger LOG = Constants.LOG;

    private static final String NAME = "locksmith_chest_locks";
    private static final String TAG_LOCKS = "Locks";
    private static final String TAG_POS = "Pos";
    private static final String TAG_HASH = "Hash";

    private final Long2ObjectOpenHashMap<String> locks = new Long2ObjectOpenHashMap<>();

    public ChestLockSavedData() {
    }

    public static ChestLockSavedData get(ServerLevel level) {
        try {
            Supplier<ChestLockSavedData> constructor = ChestLockSavedData::new;
            BiFunction<CompoundTag, HolderLookup.Provider, ChestLockSavedData> loader = ChestLockSavedData::load;

            SavedData.Factory<ChestLockSavedData> factory = new SavedData.Factory<>(constructor, loader);
            return level.getDataStorage().computeIfAbsent(factory, NAME);
        } catch (Throwable t) {
            LOG.error("[Locksmith][ChestLockSavedData] Failed to get SavedData for level {}.",
                    (level == null ? "null" : level.dimension().location()), t);
            return new ChestLockSavedData();
        }
    }

    public static ChestLockSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        ChestLockSavedData data = new ChestLockSavedData();
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

            LOG.info("[Locksmith][ChestLockSavedData] Loaded {} chest lock(s).", data.locks.size());
        } catch (Throwable t) {
            LOG.error("[Locksmith][ChestLockSavedData] load failed (non-fatal).", t);
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
            LOG.error("[Locksmith][ChestLockSavedData] save failed (non-fatal).", t);
        }
        return tag;
    }

    public boolean isLockedLong(long posLong) {
        return locks.containsKey(posLong);
    }

    public String getHashLong(long posLong) {
        String v = locks.get(posLong);
        return v == null ? "" : v;
    }

    public boolean putLockLong(long posLong, String hash) {
        try {
            if (hash == null || hash.isBlank()) return false;

            String prev = locks.putIfAbsent(posLong, hash);
            if (prev == null) {
                setDirty();
                if (LOG.isInfoEnabled()) {
                    LOG.info("[Locksmith][ChestLockSavedData] Added chest lock at posLong={} hashLen={}", posLong, hash.length());
                }
                return true;
            }
            return false;
        } catch (Throwable t) {
            LOG.error("[Locksmith][ChestLockSavedData] putLockLong failed (non-fatal).", t);
            return false;
        }
    }

    public boolean removeLockLong(long posLong) {
        try {
            String removed = locks.remove(posLong);
            if (removed != null) {
                setDirty();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][ChestLockSavedData] Removed chest lock at posLong={} hashLen={}", posLong, removed.length());
                } else {
                    LOG.info("[Locksmith][ChestLockSavedData] Removed chest lock at posLong={}", posLong);
                }
                return true;
            }
            return false;
        } catch (Throwable t) {
            LOG.error("[Locksmith][ChestLockSavedData] removeLockLong failed (non-fatal).", t);
            return false;
        }
    }

    public int cleanupInvalidBlocks(ServerLevel level, int maxToCheck) {
        int removedCount = 0;
        try {
            if (level == null) return 0;
            if (locks.isEmpty()) return 0;

            int checked = 0;
            long[] keys = locks.keySet().toLongArray();

            for (long posLong : keys) {
                if (maxToCheck > 0 && checked >= maxToCheck) break;
                checked++;

                BlockPos pos;
                try {
                    pos = BlockPos.of(posLong);
                } catch (Throwable ignored) {
                    removeLockLong(posLong);
                    removedCount++;
                    continue;
                }

                BlockState st;
                try {
                    st = level.getBlockState(pos);
                } catch (Throwable t) {
                    continue;
                }

                if (st == null || st.isAir()) {
                    boolean did = removeLockLong(posLong);
                    if (did) removedCount++;
                }
            }

            if (removedCount > 0) {
                LOG.info("[Locksmith][ChestLockSavedData] Cleanup removed {} stale lock(s). checked={}", removedCount, checked);
            }
        } catch (Throwable t) {
            LOG.error("[Locksmith][ChestLockSavedData] cleanupInvalidBlocks failed (non-fatal).", t);
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
            LOG.warn("[Locksmith][ChestLockSavedData] snapshotLocks failed (non-fatal).", t);
            return Collections.emptyMap();
        }
    }
}
