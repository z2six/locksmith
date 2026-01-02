// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/ClientDoorLockState.java
package org.z2six.locksmith.render;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongList;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

/**
 * Client-only: stores locked door positions (asLong) for the current dimension.
 * For now this is a simple set; later we can chunk-index and dimension-separate.
 */
public final class ClientDoorLockState {

    private static final Logger LOG = Constants.LOG;

    private static final LongSet LOCKED_DOORS = new LongOpenHashSet();

    private ClientDoorLockState() {
        // no-op
    }

    public static LongSet getSnapshot() {
        try {
            // Return the backing set; renderer only iterates.
            return LOCKED_DOORS;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientDoorLockState] getSnapshot failed (non-fatal).", t);
            return new LongOpenHashSet();
        }
    }

    public static void setAll(LongList pos) {
        try {
            LOCKED_DOORS.clear();
            if (pos != null) {
                for (int i = 0; i < pos.size(); i++) {
                    LOCKED_DOORS.add(pos.getLong(i));
                }
            }
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientDoorLockState] setAll failed (non-fatal).", t);
        }
    }

    public static void add(long posLong) {
        try {
            LOCKED_DOORS.add(posLong);
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientDoorLockState] add failed (non-fatal).", t);
        }
    }
}
