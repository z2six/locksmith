// neoforge/src/main/java/org/z2six/locksmith/render/ClientChestOpenBlocker.java
package org.z2six.locksmith.render;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

public final class ClientChestOpenBlocker {

    private static final Logger LOG = Constants.LOG;

    private static final Long2LongOpenHashMap EXPIRES_AT = new Long2LongOpenHashMap();

    static {
        try {
            EXPIRES_AT.defaultReturnValue(Long.MIN_VALUE);
        } catch (Throwable ignored) {
        }
    }

    private ClientChestOpenBlocker() {
    }

    public static void blockOpenForTicks(long chestPosLong, long nowGameTime, int ticks) {
        try {
            if (ticks <= 0) ticks = 1;
            long expire = nowGameTime + ticks;

            synchronized (EXPIRES_AT) {
                long prev = EXPIRES_AT.get(chestPosLong);
                if (prev < expire) {
                    EXPIRES_AT.put(chestPosLong, expire);
                }
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("[Locksmith][ClientChestOpenBlocker] blockOpenForTicks posLong={} now={} ticks={} expire={}",
                        chestPosLong, nowGameTime, ticks, expire);
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientChestOpenBlocker] blockOpenForTicks failed (non-fatal).", t);
        }
    }

    public static boolean shouldBlockOpenNow(long chestPosLong, long nowGameTime) {
        try {
            long expire;
            synchronized (EXPIRES_AT) {
                expire = EXPIRES_AT.get(chestPosLong);
                if (expire == Long.MIN_VALUE) {
                    return false;
                }
                if (nowGameTime > expire) {
                    EXPIRES_AT.remove(chestPosLong);
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientChestOpenBlocker] shouldBlockOpenNow failed (non-fatal).", t);
            return false;
        }
    }

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
                LOG.debug("[Locksmith][ClientChestOpenBlocker] cleanupExpired removed={} checked={}", removed, checked);
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientChestOpenBlocker] cleanupExpired failed (non-fatal).", t);
        }
    }

    public static void clear() {
        try {
            synchronized (EXPIRES_AT) {
                EXPIRES_AT.clear();
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientChestOpenBlocker] clear failed (non-fatal).", t);
        }
    }
}
