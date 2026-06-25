package org.z2six.locksmith.render;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

public final class ClientGenericLockPulse {
    private static final int OPEN_TICKS = 10;
    private static final Long2LongOpenHashMap OPEN_UNTIL = new Long2LongOpenHashMap();

    static {
        OPEN_UNTIL.defaultReturnValue(Long.MIN_VALUE);
    }

    private ClientGenericLockPulse() {
    }

    public static void trigger(long posLong, long nowTick) {
        OPEN_UNTIL.put(posLong, nowTick + OPEN_TICKS);
    }

    public static boolean isOpen(long posLong, long nowTick) {
        long until = OPEN_UNTIL.get(posLong);
        if (until == Long.MIN_VALUE) {
            return false;
        }
        if (nowTick <= until) {
            return true;
        }
        OPEN_UNTIL.remove(posLong);
        return false;
    }
}
