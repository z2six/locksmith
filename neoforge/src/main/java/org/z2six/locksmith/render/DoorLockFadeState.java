// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/DoorLockFadeState.java
package org.z2six.locksmith.render;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

/**
 * Tracks per-position fade state for door/chest locks when opened.
 *
 * Timeline (for each posLong):
 * - Closed:
 *   - We clear state and return frame=-1, alpha=1.
 * - Open:
 *   - First call: we record nowTick and start at frame 0, alpha=1.
 *   - Then over TOTAL_TICKS we step frames 0..(FRAME_COUNT-1) and alpha 1→0.
 *   - After TOTAL_TICKS we mark the animation as FINISHED and keep returning -2
 *     while the block remains open (no looping).
 *   - When it closes, we clear state so the next open can play again.
 *
 * Currently tuned for:
 *   FRAME_COUNT      = 5
 *   TICKS_PER_FRAME  = 4 (change this to 2 or 1 if you want faster animation)
 *   TOTAL_TICKS      = FRAME_COUNT * TICKS_PER_FRAME
 */
public final class DoorLockFadeState {

    private static final Logger LOG = Constants.LOG;

    private static final Long2LongOpenHashMap OPENED_AT = new Long2LongOpenHashMap();

    /** Sentinel meaning "no state stored". */
    private static final long NO_STATE = Long.MIN_VALUE;

    /** Sentinel meaning "animation finished (while still open)". */
    private static final long FINISHED = -1L;

    /** Number of unlocked models: lock_iron_unlocked0..4 → 5 frames. */
    private static final int FRAME_COUNT = 5;

    /** Ticks to spend per frame. (Change this for speed: 4 → 2 or 1) */
    private static final int TICKS_PER_FRAME = 2;

    /** Total ticks for the full unlock → fade-out sequence. */
    private static final int TOTAL_TICKS = FRAME_COUNT * TICKS_PER_FRAME;

    static {
        try {
            OPENED_AT.defaultReturnValue(NO_STATE);
        } catch (Throwable ignored) {
        }
    }

    private DoorLockFadeState() {
    }

    /**
     * Compute the animation frame index for a given block position.
     *
     * @param posLong block position as long
     * @param open    current open state of the door/chest
     * @param nowTick world game time
     * @return
     *   - -1 → closed (no unlock animation; draw locked model)
     *   -  0..(FRAME_COUNT-1) → unlocked frames
     *   - -2 → fade finished (do not render)
     */
    public static int getFrameIndex(long posLong, boolean open, long nowTick) {
        try {
            if (!open) {
                long removed = OPENED_AT.remove(posLong);
                if (removed != NO_STATE && LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][DoorLockFadeState] Reset fade state for posLong={} (closed).", posLong);
                }
                return -1;
            }

            long openedAt = OPENED_AT.get(posLong);

            // If we previously finished the animation for this open cycle, keep reporting -2
            // so the lock stays gone until the block closes again.
            if (openedAt == FINISHED) {
                return -2;
            }

            // First time seeing this position in an open state: start the animation.
            if (openedAt == NO_STATE) {
                OPENED_AT.put(posLong, nowTick);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][DoorLockFadeState] Start fade for posLong={} at tick={}.", posLong, nowTick);
                }
                return 0;
            }

            long elapsed = nowTick - openedAt;
            if (elapsed < 0L) {
                // Time went backwards; restart fade once.
                OPENED_AT.put(posLong, nowTick);
                return 0;
            }

            if (elapsed >= TOTAL_TICKS) {
                // Mark as finished but DO NOT remove; this prevents looping while still open.
                OPENED_AT.put(posLong, FINISHED);
                return -2;
            }

            // N ticks per frame over TOTAL_TICKS:
            // TICKS_PER_FRAME=4, FRAME_COUNT=5 → 0-3 → 0, 4-7 → 1, ... 16-19 → 4
            int frame = (int) (elapsed / TICKS_PER_FRAME);
            if (frame < 0) frame = 0;
            if (frame >= FRAME_COUNT) frame = FRAME_COUNT - 1;
            return frame;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockFadeState] getFrameIndex failed (non-fatal). posLong={} open={} nowTick={}",
                    posLong, open, nowTick, t);
            return -1;
        }
    }

    /**
     * Compute alpha factor (0..1) for the fade, using the same timeline and internal state
     * as {@link #getFrameIndex(long, boolean, long)}.
     *
     * IMPORTANT: callers should usually call getFrameIndex(...) FIRST so that the
     * openedAt timestamp is initialized, then call this method.
     *
     * @param posLong block position as long
     * @param open    current open state of the door/chest
     * @param nowTick world game time
     * @return alpha in [0..1]. For closed → 1; for finished fade → 0.
     */
    public static float getAlpha(long posLong, boolean open, long nowTick) {
        try {
            if (!open) {
                // Closed: visually fully opaque (locked model). getFrameIndex already clears state.
                return 1.0f;
            }

            long openedAt = OPENED_AT.get(posLong);

            if (openedAt == FINISHED) {
                // Animation already finished this open cycle.
                return 0.0f;
            }

            if (openedAt == NO_STATE) {
                // If called before getFrameIndex, treat as just-opened.
                return 1.0f;
            }

            long elapsed = nowTick - openedAt;
            if (elapsed <= 0L) {
                return 1.0f;
            }

            if (elapsed >= TOTAL_TICKS) {
                // Fade fully done.
                return 0.0f;
            }

            float progress = (float) elapsed / (float) TOTAL_TICKS; // 0..1
            float alpha = 1.0f - progress; // 1 → 0
            if (alpha < 0.0f) alpha = 0.0f;
            if (alpha > 1.0f) alpha = 1.0f;
            return alpha;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockFadeState] getAlpha failed (non-fatal). posLong={} open={} nowTick={}",
                    posLong, open, nowTick, t);
            return 1.0f;
        }
    }
}
