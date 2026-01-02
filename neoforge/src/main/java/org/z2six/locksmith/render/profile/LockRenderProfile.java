// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/profile/LockRenderProfile.java
package org.z2six.locksmith.render.profile;

import net.minecraft.resources.ResourceLocation;

/**
 * Flat render profile for a single block id.
 * This is what gets sent over the wire (see SyncLockRenderProfilesPayload).
 */
public final class LockRenderProfile {

    public final LockTargetType type;
    public final ResourceLocation blockId;

    public final double offsetX;
    public final double offsetY;
    public final double offsetZ;

    public final float rotX;
    public final float rotY;
    public final float rotZ;

    public final float scale;

    public final double hingeNudgeLeft;
    public final double hingeNudgeRight;

    public LockRenderProfile(
            LockTargetType type,
            ResourceLocation blockId,
            double offsetX,
            double offsetY,
            double offsetZ,
            float rotX,
            float rotY,
            float rotZ,
            float scale,
            double hingeNudgeLeft,
            double hingeNudgeRight
    ) {
        this.type = type;
        this.blockId = blockId;
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
        return type != null && blockId != null;
    }
}
