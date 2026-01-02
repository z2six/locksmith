// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/profile/LockRenderProfile.java
package org.z2six.locksmith.render.profile;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

/**
 * A render profile says:
 * - which target (block id) this applies to
 * - what type of lockable target it is (door/chest/etc)
 * - transform data for rendering the lock item
 *
 * This is purely visual. Gameplay lock state is kept elsewhere.
 */
public final class LockRenderProfile {

    private static final Logger LOG = Constants.LOG;

    public final LockTargetType type;
    public final ResourceLocation target;

    // Local-space transform (after we rotate for facing).
    public final double offsetX;
    public final double offsetY;
    public final double offsetZ;

    public final float rotX;
    public final float rotY;
    public final float rotZ;

    public final float scale;

    // Door-specific optional tuning. Safe to ignore for non-door types.
    public final double hingeNudgeLeft;
    public final double hingeNudgeRight;

    public LockRenderProfile(
            LockTargetType type,
            ResourceLocation target,
            double offsetX, double offsetY, double offsetZ,
            float rotX, float rotY, float rotZ,
            float scale,
            double hingeNudgeLeft, double hingeNudgeRight
    ) {
        this.type = (type == null) ? LockTargetType.DOOR : type;
        this.target = target;

        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;

        this.rotX = rotX;
        this.rotY = rotY;
        this.rotZ = rotZ;

        this.scale = scale;

        this.hingeNudgeLeft = hingeNudgeLeft;
        this.hingeNudgeRight = hingeNudgeRight;
    }

    public boolean isValid() {
        return this.target != null;
    }

    public LockRenderProfile withTarget(ResourceLocation newTarget) {
        return new LockRenderProfile(
                this.type,
                newTarget,
                this.offsetX, this.offsetY, this.offsetZ,
                this.rotX, this.rotY, this.rotZ,
                this.scale,
                this.hingeNudgeLeft, this.hingeNudgeRight
        );
    }

    /**
     * Default door profile = your current LockRenderTuning.
     * (We keep this so if config is missing, you still get perfect rendering.)
     */
    public static LockRenderProfile defaultDoor(ResourceLocation target,
                                                double offsetX, double offsetY, double offsetZ,
                                                float rotX, float rotY, float rotZ,
                                                float scale,
                                                double hingeLeft, double hingeRight) {
        if (target == null) {
            LOG.warn("[Locksmith][LockRenderProfile] defaultDoor called with null target.");
        }
        return new LockRenderProfile(
                LockTargetType.DOOR,
                target,
                offsetX, offsetY, offsetZ,
                rotX, rotY, rotZ,
                scale,
                hingeLeft, hingeRight
        );
    }
}
