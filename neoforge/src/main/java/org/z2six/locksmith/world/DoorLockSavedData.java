// MainFile: neoforge/src/main/java/org/z2six/locksmith/world/DoorLockSavedData.java
package org.z2six.locksmith.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Per-dimension SavedData: stores door locks keyed by BlockPos.asLong() -> hash hex string.
 * Persists across server restarts.
 *
 * MC 1.21+ note:
 * - save(...) signature requires HolderLookup.Provider
 * - Factory loader signature takes (CompoundTag, HolderLookup.Provider)
 */
public class DoorLockSavedData extends SavedData {

    private static final Logger LOG = Constants.LOG;

    private static final String NAME = Constants.MOD_ID + "_door_locks";
    private static final String TAG_LOCKS = "Locks";
    private static final String TAG_POS = "Pos";
    private static final String TAG_HASH = "Hash";

    // posLong -> hash
    private final Map<Long, String> locks = new HashMap<>();

    public DoorLockSavedData() {
        // empty
    }

    /**
     * Get/create the SavedData for this dimension.
     */
    public static DoorLockSavedData get(ServerLevel level) {
        try {
            // Explicit generics to avoid "cannot infer type arguments" on newer mappings.
            Supplier<DoorLockSavedData> constructor = DoorLockSavedData::new;
            BiFunction<CompoundTag, HolderLookup.Provider, DoorLockSavedData> loader = DoorLockSavedData::load;

            SavedData.Factory<DoorLockSavedData> factory = new SavedData.Factory<>(constructor, loader);

            return level.getDataStorage().computeIfAbsent(factory, NAME);
        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockSavedData] Failed to get SavedData for level {}.",
                    (level == null ? "null" : level.dimension().location()), t);
            // Fallback: non-persisting instance to avoid crashes
            return new DoorLockSavedData();
        }
    }

    /**
     * Loader for 1.21+ SavedData Factory.
     */
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

    /**
     * Save hook for 1.21+.
     */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        try {
            ListTag list = new ListTag();
            for (Map.Entry<Long, String> en : locks.entrySet()) {
                String hash = en.getValue();
                if (hash == null || hash.isBlank()) continue;

                CompoundTag e = new CompoundTag();
                e.putLong(TAG_POS, en.getKey());
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

    public String getHash(BlockPos pos) {
        if (pos == null) return "";
        String v = locks.get(pos.asLong());
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

    public Map<Long, String> snapshotLocks() {
        try {
            return Collections.unmodifiableMap(new HashMap<>(locks));
        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockSavedData] snapshotLocks failed (non-fatal).", t);
            return Collections.emptyMap();
        }
    }
}
