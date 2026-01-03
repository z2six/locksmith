// neoforge/src/main/java/org/z2six/locksmith/render/LockRenderTuning.java
package org.z2six.locksmith.render;

public final class LockRenderTuning {

    private LockRenderTuning() {
    }

    // ----- DOORS -----
    public static double OFFSET_X = -0.05;   // base left/right relative to door face (local X after facing rotation)
    public static double OFFSET_Y = 0.5;     // up/down
    public static double OFFSET_Z = -0.5;    // out from the door surface (towards player) in our local frame

    public static float ROT_X = 0.0f;
    public static float ROT_Y = 0.0f;
    public static float ROT_Z = 0.0f;

    public static float SCALE = 0.75f;

    public static double NUDGE_HINGE_LEFT = 0.18;   // magnitude when hinge is LEFT (applied as -NUDGE_HINGE_LEFT)
    public static double NUDGE_HINGE_RIGHT = 0.325; // magnitude when hinge is RIGHT (applied as +NUDGE_HINGE_RIGHT)

    // ----- CHESTS -----
    // Defaults are intentionally different from doors.
    // Server admins can override per-block in locksmith_profiles.json.
    public static double CHEST_OFFSET_X = 0.0;    // centered on chest face
    public static double CHEST_OFFSET_Y = 0.35;   // slightly below mid
    public static double CHEST_OFFSET_Z = -0.45;  // just in front of chest face

    public static float CHEST_ROT_X = 0.0f;
    public static float CHEST_ROT_Y = 0.0f;
    public static float CHEST_ROT_Z = 0.0f;

    public static float CHEST_SCALE = 0.75f;

    // Not used for chests, but kept for uniform serialization.
    public static double CHEST_NUDGE_LEFT = 0.0;
    public static double CHEST_NUDGE_RIGHT = 0.0;
}
