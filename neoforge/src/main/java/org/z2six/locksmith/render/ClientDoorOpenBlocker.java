// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/ClientDoorOpenBlocker.java
package org.z2six.locksmith.render;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

/**
 * Client-only helper to suppress the *predicted* "door opens" visual for a few ticks.
 *
 * Why this exists:
 * - Client prediction can open doors locally before server authoritative state arrives.
 * - Even if we close the door immediately after, some clients render 1 frame of "open".
 * - So we also intercept the actual open operation and cancel it during a short window.
 */
public final class ClientDoorOpenBlocker {

    private static final Logger LOG = Constants.LOG;

    /**
     * doorPosLong -> expireGameTime (inclusive)
     */
    private static final Long2LongOpenHashMap EXPIRES_AT = new Long2LongOpenHashMap();

    static {
        try {
            EXPIRES_AT.defaultReturnValue(Long.MIN_VALUE);
        } catch (Throwable ignored) {
        }
    }

    private ClientDoorOpenBlocker() {
    }

    /**
     * Mark a door position as "do not allow predicted open" for a few ticks.
     */
    public static void blockOpenForTicks(long doorPosLong, long nowGameTime, int ticks) {
        try {
            if (ticks <= 0) ticks = 1;
            long expire = nowGameTime + ticks;

            synchronized (EXPIRES_AT) {
                long prev = EXPIRES_AT.get(doorPosLong);
                if (prev < expire) {
                    EXPIRES_AT.put(doorPosLong, expire);
                }
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("[Locksmith][ClientDoorOpenBlocker] blockOpenForTicks posLong={} now={} ticks={} expire={}",
                        doorPosLong, nowGameTime, ticks, expire);
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientDoorOpenBlocker] blockOpenForTicks failed (non-fatal).", t);
        }
    }

    /**
     * Returns true if we should cancel an attempt to open this door *right now*.
     */
    public static boolean shouldBlockOpenNow(long doorPosLong, long nowGameTime) {
        try {
            long expire;
            synchronized (EXPIRES_AT) {
                expire = EXPIRES_AT.get(doorPosLong);
                if (expire == Long.MIN_VALUE) {
                    return false;
                }
                if (nowGameTime > expire) {
                    EXPIRES_AT.remove(doorPosLong);
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientDoorOpenBlocker] shouldBlockOpenNow failed (non-fatal).", t);
            return false;
        }
    }

    /**
     * Opportunistic cleanup. Not required, but keeps the map small during long sessions.
     */
    public static void cleanupExpired(long nowGameTime, int maxToCheck) {
        try {
            if (maxToCheck <= 0) maxToCheck = 128;

            int checked = 0;
            int removed = 0;

            synchronized (EXPIRES_AT) {
                LongIterator it = EXPIRES_AT.keySet().iterator();
                while (it.hasNext() && checked < maxToCheck) {
                    long key = it.nextLong();
                    long exp = EXPIRES_AT.get(key);
                    checked++;
                    if (nowGameTime > exp) {
                        it.remove();
                        removed++;
                    }
                }
            }

            if (removed > 0 && LOG.isDebugEnabled()) {
                LOG.debug("[Locksmith][ClientDoorOpenBlocker] cleanupExpired removed={} checked={}", removed, checked);
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientDoorOpenBlocker] cleanupExpired failed (non-fatal).", t);
        }
    }
}
