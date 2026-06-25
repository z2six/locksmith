// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/profile/LockableBlockValidationResult.java
package org.z2six.locksmith.render.profile;

import net.minecraft.resources.ResourceLocation;

public record LockableBlockValidationResult(
        ResourceLocation blockId,
        LockableBlockValidationStatus status,
        String message
) {
    public boolean okForSave() {
        return status == LockableBlockValidationStatus.OK || status == LockableBlockValidationStatus.DISABLED;
    }
}
