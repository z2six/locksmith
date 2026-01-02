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

    // Offsets in block-local space (blocks are 1.0 unit)
    // Convention: (0,0,0) is block corner; we'll translate to block center before applying offsets.
    public static double OFFSET_X = 0.0;     // left/right relative to door face
    public static double OFFSET_Y = 0.0;     // up/down
    public static double OFFSET_Z = 0.44;    // out from the door surface (towards player)

    // Rotations in degrees (applied after door-facing rotation)
    public static float ROT_X = 0.0f;
    public static float ROT_Y = 0.0f;
    public static float ROT_Z = 0.0f;

    // Uniform scale
    public static float SCALE = 0.75f;

    // Hinge-side nudge (so the lock sits nicely depending on hinge)
    public static double HINGE_NUDGE = 0.18;
}
