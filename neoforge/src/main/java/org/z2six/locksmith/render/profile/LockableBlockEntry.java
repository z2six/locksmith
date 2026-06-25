// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/profile/LockableBlockEntry.java
package org.z2six.locksmith.render.profile;

import net.minecraft.resources.ResourceLocation;

public record LockableBlockEntry(
        ResourceLocation blockId,
        LockTargetType type,
        LockTransform transform,
        boolean builtinDefault,
        boolean enabled
) {
    public LockableBlockEntry {
        if (type == null) {
            type = LockTargetType.GENERIC;
        }
        if (transform == null) {
            transform = defaultTransform(type);
        }
    }

    public LockRenderProfile toRenderProfile() {
        LockTransform t = transform.clamped();
        double hingeLeft = type == LockTargetType.CHEST ? t.doubleNudgeX() : t.hingeNudgeLeft();
        double hingeRight = type == LockTargetType.CHEST ? t.doubleNudgeX() : t.hingeNudgeRight();
        return new LockRenderProfile(
                type,
                blockId,
                t.offsetX(), t.offsetY(), t.offsetZ(),
                t.rotX(), t.rotY(), t.rotZ(),
                t.scale(),
                hingeLeft,
                hingeRight
        );
    }

    public LockableBlockEntry withTransform(LockTransform next) {
        return new LockableBlockEntry(blockId, type, next, builtinDefault, enabled);
    }

    public LockableBlockEntry withBlockId(ResourceLocation nextBlockId) {
        return new LockableBlockEntry(nextBlockId, type, transform, builtinDefault, enabled);
    }

    public LockableBlockEntry withType(LockTargetType nextType) {
        LockTransform nextTransform = defaultTransform(nextType);
        return new LockableBlockEntry(blockId, nextType, nextTransform, builtinDefault, enabled);
    }

    public LockableBlockEntry withEnabled(boolean nextEnabled) {
        return new LockableBlockEntry(blockId, type, transform, builtinDefault, nextEnabled);
    }

    private static LockTransform defaultTransform(LockTargetType type) {
        return switch (type == null ? LockTargetType.GENERIC : type) {
            case CHEST -> LockTransform.chestDefault();
            case GENERIC -> LockTransform.genericDefault();
            case DOOR -> LockTransform.doorDefault();
        };
    }
}
