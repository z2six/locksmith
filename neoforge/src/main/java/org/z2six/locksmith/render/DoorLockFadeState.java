// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/DoorLockFadeState.java
package org.z2six.locksmith.render;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

/**
 * Tracks per-position fade state for door/chest lock rendering.
 *
 * We support TWO directions:
 *
 * 1) OPENING (door/chest becomes open):
 *    - show unlocked frames 0..4 while alpha fades OUT (1 -> 0)
 *    - when finished, return -2 (do not render) until it closes again.
 *
 * 2) CLOSING (door/chest becomes closed):
 *    - show unlocked frames 4..0 while alpha fades IN (0 -> 1)
 *    - when finished, return -1 (render locked model normally).
 *
 * Render contract for getFrameIndex(...):
 *   - -2 -> do not render (used only for OPEN while finished)
 *   - -1 -> render locked model (closed steady-state)
 *   -  0..(FRAME_COUNT-1) -> render unlocked frameIndex with alpha from getAlpha(...)
 *
 * IMPORTANT:
 * - This is purely client-side render state. It doesn't change gameplay.
 * - We never chain beyond this position; one lock = one animation timeline.
 */
public final class DoorLockFadeState {

    private static final Logger LOG = Constants.LOG;

    /** start tick of current animation (opening/closing) */
    private static final Long2LongOpenHashMap START_AT = new Long2LongOpenHashMap();

    /** mode per posLong */
    private static final Long2ByteOpenHashMap MODE = new Long2ByteOpenHashMap();

    /** last known open state per posLong (0=closed, 1=open) */
    private static final Long2ByteOpenHashMap LAST_OPEN = new Long2ByteOpenHashMap();

    // modes
    private static final byte MODE_NONE = 0;
    private static final byte MODE_OPENING = 1;      // open -> play frames 0..4 while alpha fades OUT
    private static final byte MODE_OPEN_FINISHED = 2; // still open but animation finished (do not render)
    private static final byte MODE_CLOSING = 3;      // closed -> play frames 4..0 while alpha fades IN

    /** Sentinel meaning "no start tick stored". */
    private static final long NO_STATE = Long.MIN_VALUE;

    /** Number of unlocked models: lock_iron_unlocked0..4 -> 5 frames. */
    private static final int FRAME_COUNT = 5;

    /** Ticks to spend per frame. */
    private static final int TICKS_PER_FRAME = 2;

    /** Total ticks for the full sequence. */
    private static final int TOTAL_TICKS = FRAME_COUNT * TICKS_PER_FRAME;

    static {
        try {
            START_AT.defaultReturnValue(NO_STATE);
        } catch (Throwable ignored) {
        }
        try {
            MODE.defaultReturnValue(MODE_NONE);
        } catch (Throwable ignored) {
        }
        try {
            LAST_OPEN.defaultReturnValue((byte) -1);
        } catch (Throwable ignored) {
        }
    }

    private DoorLockFadeState() {
    }

    /**
     * Returns the frame index the renderer should use for the unlocked model, or a sentinel:
     *
     * @return
     *   -2 -> do not render (open, finished fade-out)
     *   -1 -> render locked model (closed steady-state)
     *   0..4 -> unlocked frame to render (alpha provided by getAlpha)
     */
    public static int getFrameIndex(long posLong, boolean open, long nowTick) {
        try {
            byte last = LAST_OPEN.get(posLong);
            byte now = (byte) (open ? 1 : 0);

            if (last == (byte) -1) {
                // first time we see this pos
                LAST_OPEN.put(posLong, now);
                MODE.put(posLong, MODE_NONE);
                START_AT.put(posLong, NO_STATE);

                // If it's already open at first sight, start opening animation.
                if (open) {
                    MODE.put(posLong, MODE_OPENING);
                    START_AT.put(posLong, nowTick);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[Locksmith][DoorLockFadeState] Init posLong={} as OPENING at tick={}", posLong, nowTick);
                    }
                    return 0;
                }

                // Closed steady-state.
                return -1;
            }

            // Detect transitions
            if (last != now) {
                LAST_OPEN.put(posLong, now);

                if (open) {
                    // closed -> open
                    MODE.put(posLong, MODE_OPENING);
                    START_AT.put(posLong, nowTick);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[Locksmith][DoorLockFadeState] Transition CLOSED->OPEN at posLong={} tick={}", posLong, nowTick);
                    }
                    return 0;
                } else {
                    // open -> closed
                    MODE.put(posLong, MODE_CLOSING);
                    START_AT.put(posLong, nowTick);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[Locksmith][DoorLockFadeState] Transition OPEN->CLOSED at posLong={} tick={}", posLong, nowTick);
                    }
                    // Closing starts immediately: show first closing frame (frame 4) at alpha ~0.
                    return FRAME_COUNT - 1;
                }
            }

            // No transition: drive current mode
            byte mode = MODE.get(posLong);
            long start = START_AT.get(posLong);

            if (open) {
                // OPEN steady-state:
                // - if OPENING, progress to finished then return -2
                // - if OPEN_FINISHED, keep returning -2
                // - if CLOSING (shouldn't happen while open), restart OPENING
                if (mode == MODE_OPEN_FINISHED) {
                    return -2;
                }
                if (mode != MODE_OPENING) {
                    // recovery path
                    MODE.put(posLong, MODE_OPENING);
                    START_AT.put(posLong, nowTick);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[Locksmith][DoorLockFadeState] Recovery: forcing OPENING at posLong={} tick={}", posLong, nowTick);
                    }
                    return 0;
                }

                if (start == NO_STATE) {
                    START_AT.put(posLong, nowTick);
                    return 0;
                }

                long elapsed = nowTick - start;
                if (elapsed < 0L) {
                    START_AT.put(posLong, nowTick);
                    return 0;
                }

                if (elapsed >= TOTAL_TICKS) {
                    MODE.put(posLong, MODE_OPEN_FINISHED);
                    return -2;
                }

                int frame = (int) (elapsed / TICKS_PER_FRAME);
                if (frame < 0) frame = 0;
                if (frame >= FRAME_COUNT) frame = FRAME_COUNT - 1;
                return frame;
            } else {
                // CLOSED steady-state:
                // - if CLOSING, play reverse frames then finish into locked (-1)
                // - otherwise locked (-1)
                if (mode != MODE_CLOSING) {
                    // ensure we don't keep stale opening finished state forever
                    if (mode != MODE_NONE) {
                        MODE.put(posLong, MODE_NONE);
                        START_AT.put(posLong, NO_STATE);
                    }
                    return -1;
                }

                if (start == NO_STATE) {
                    START_AT.put(posLong, nowTick);
                    return FRAME_COUNT - 1;
                }

                long elapsed = nowTick - start;
                if (elapsed < 0L) {
                    START_AT.put(posLong, nowTick);
                    return FRAME_COUNT - 1;
                }

                if (elapsed >= TOTAL_TICKS) {
                    // Done closing: return to locked steady-state
                    MODE.put(posLong, MODE_NONE);
                    START_AT.put(posLong, NO_STATE);
                    return -1;
                }

                // Reverse frame order for closing: 4..0
                int forward = (int) (elapsed / TICKS_PER_FRAME); // 0..4
                if (forward < 0) forward = 0;
                if (forward >= FRAME_COUNT) forward = FRAME_COUNT - 1;

                return (FRAME_COUNT - 1) - forward;
            }

        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockFadeState] getFrameIndex failed (non-fatal). posLong={} open={} nowTick={}",
                    posLong, open, nowTick, t);
            return open ? -2 : -1;
        }
    }

    /**
     * Alpha factor (0..1) for the current animation mode.
     *
     * Usage pattern in renderer:
     * - call getFrameIndex(...) first (initializes state on transitions)
     * - if frameIndex >= 0, call getAlpha(...) and render unlocked frame with that alpha
     *
     * Semantics:
     * - OPENING: alpha fades OUT (1 -> 0)
     * - CLOSING: alpha fades IN  (0 -> 1)
     * - locked steady-state: alpha 1
     * - open finished: alpha 0
     */
    public static float getAlpha(long posLong, boolean open, long nowTick) {
        try {
            byte mode = MODE.get(posLong);
            long start = START_AT.get(posLong);

            if (open) {
                if (mode == MODE_OPEN_FINISHED) {
                    return 0.0F;
                }
                if (mode != MODE_OPENING) {
                    // If renderer calls alpha without calling getFrameIndex first, be safe.
                    return 1.0F;
                }
                if (start == NO_STATE) return 1.0F;

                long elapsed = nowTick - start;
                if (elapsed <= 0L) return 1.0F;
                if (elapsed >= TOTAL_TICKS) return 0.0F;

                float progress = (float) elapsed / (float) TOTAL_TICKS; // 0..1
                float alpha = 1.0F - progress; // 1->0
                if (alpha < 0.0F) alpha = 0.0F;
                if (alpha > 1.0F) alpha = 1.0F;
                return alpha;
            } else {
                if (mode != MODE_CLOSING) {
                    return 1.0F; // locked steady-state
                }
                if (start == NO_STATE) return 0.0F;

                long elapsed = nowTick - start;
                if (elapsed <= 0L) return 0.0F;
                if (elapsed >= TOTAL_TICKS) return 1.0F;

                float progress = (float) elapsed / (float) TOTAL_TICKS; // 0..1
                float alpha = progress; // 0->1
                if (alpha < 0.0F) alpha = 0.0F;
                if (alpha > 1.0F) alpha = 1.0F;
                return alpha;
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockFadeState] getAlpha failed (non-fatal). posLong={} open={} nowTick={}",
                    posLong, open, nowTick, t);
            return 1.0F;
        }
    }
}
