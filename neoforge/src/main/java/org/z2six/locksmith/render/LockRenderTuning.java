// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/LockRenderTuning.java
package org.z2six.locksmith.render;

/**
 * Hardcoded tuning parameters for lock rendering on doors.
 * These are intentionally simple edit-and-recompile values for now.
 *
 * Later: replaced by server-config driven per-block transform data.
 */
public final class LockRenderTuning {

    private LockRenderTuning() {
        // no-op
    }

    /**
     * Offsets in block-local space (blocks are 1.0 unit)
     * Convention: (0,0,0) is block corner; we translate to block center before applying offsets.
     *
     * IMPORTANT:
     * - These offsets are applied AFTER rotating the pose stack by the door's facing (Y rotation).
     * - So OFFSET_X is "left/right across the door face" in our chosen local frame.
     */
    public static double OFFSET_X = -0.05;   // base left/right relative to door face (local X after facing rotation)
    public static double OFFSET_Y = 0.5;     // up/down
    public static double OFFSET_Z = -0.5;    // out from the door surface (towards player) in our local frame

    /**
     * Rotations in degrees (applied after door-facing rotation)
     */
    public static float ROT_X = 0.0f;
    public static float ROT_Y = 0.0f;
    public static float ROT_Z = 0.0f;

    /**
     * Uniform scale
     */
    public static float SCALE = 0.75f;

    /**
     * Hinge compensation magnitudes.
     *
     * IMPORTANT:
     * We apply these with OPPOSITE SIGNS depending on hinge side:
     * - LEFT hinge gets NEGATIVE nudge (moves opposite direction across the face)
     * - RIGHT hinge gets POSITIVE nudge
     *
     * That is intentional and fixes mirrored placement.
     */
    public static double NUDGE_HINGE_LEFT = 0.18;   // magnitude when hinge is LEFT (applied as -NUDGE_HINGE_LEFT)
    public static double NUDGE_HINGE_RIGHT = 0.325; // magnitude when hinge is RIGHT (applied as +NUDGE_HINGE_RIGHT)
}
