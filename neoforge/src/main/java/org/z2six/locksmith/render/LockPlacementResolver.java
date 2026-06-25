// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/LockPlacementResolver.java
package org.z2six.locksmith.render;

import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import org.z2six.locksmith.render.profile.LockRenderProfile;
import org.z2six.locksmith.render.profile.LockTransform;

public final class LockPlacementResolver {
    private static final double CHEST_SINGLE_OFFSET_X = -0.025D;
    private static final double CHEST_SINGLE_OFFSET_Y = 0.05D;
    private static final double CHEST_SINGLE_OFFSET_Z = 0.45D;
    private static final float CHEST_SINGLE_ROT_X = 0.0F;
    private static final float CHEST_SINGLE_ROT_Y = 180.0F;
    private static final float CHEST_SINGLE_ROT_Z = 0.0F;
    private static final float CHEST_SINGLE_SCALE = 0.75F;
    private static final double CHEST_DOUBLE_SHIFT_FALLBACK_X = 0.18D;

    private LockPlacementResolver() {
    }

    public static ResolvedLockPlacement resolveDoor(BlockState state, LockRenderProfile profile) {
        boolean useProfile = profile != null && profile.isValid();

        double baseOffsetX = useProfile ? profile.offsetX : LockRenderTuning.OFFSET_X;
        double baseOffsetY = useProfile ? profile.offsetY : LockRenderTuning.OFFSET_Y;
        double baseOffsetZ = useProfile ? profile.offsetZ : LockRenderTuning.OFFSET_Z;

        float rotX = useProfile ? profile.rotX : LockRenderTuning.ROT_X;
        float rotY = useProfile ? profile.rotY : LockRenderTuning.ROT_Y;
        float rotZ = useProfile ? profile.rotZ : LockRenderTuning.ROT_Z;
        float scale = useProfile ? profile.scale : LockRenderTuning.SCALE;

        double hingeLeftMag = useProfile ? profile.hingeNudgeLeft : LockRenderTuning.NUDGE_HINGE_LEFT;
        double hingeRightMag = useProfile ? profile.hingeNudgeRight : LockRenderTuning.NUDGE_HINGE_RIGHT;
        DoorHingeSide hinge = state != null && state.hasProperty(DoorBlock.HINGE)
                ? state.getValue(DoorBlock.HINGE)
                : DoorHingeSide.RIGHT;
        double hingeSignedNudge = hinge == DoorHingeSide.LEFT ? -hingeLeftMag : hingeRightMag;

        return new ResolvedLockPlacement(
                baseOffsetX + hingeSignedNudge,
                baseOffsetY,
                baseOffsetZ,
                rotX,
                rotY,
                rotZ,
                scale
        );
    }

    public static ResolvedLockPlacement resolveChest(BlockState state, LockRenderProfile profile) {
        boolean useProfile = profile != null && profile.isValid();

        double baseOffsetX = useProfile ? profile.offsetX : CHEST_SINGLE_OFFSET_X;
        double baseOffsetY = useProfile ? profile.offsetY : CHEST_SINGLE_OFFSET_Y;
        double baseOffsetZ = useProfile ? profile.offsetZ : CHEST_SINGLE_OFFSET_Z;

        float rotX = useProfile ? profile.rotX : CHEST_SINGLE_ROT_X;
        float rotY = useProfile ? profile.rotY : CHEST_SINGLE_ROT_Y;
        float rotZ = useProfile ? profile.rotZ : CHEST_SINGLE_ROT_Z;
        float scale = useProfile ? profile.scale : CHEST_SINGLE_SCALE;

        double doubleNudgeX = useProfile ? profile.hingeNudgeLeft : CHEST_DOUBLE_SHIFT_FALLBACK_X;
        ChestType type = state != null && state.hasProperty(ChestBlock.TYPE)
                ? state.getValue(ChestBlock.TYPE)
                : ChestType.SINGLE;

        double finalX = baseOffsetX;
        if (type != ChestType.SINGLE && doubleNudgeX != 0.0D) {
            if (type == ChestType.LEFT) {
                finalX += doubleNudgeX;
            } else if (type == ChestType.RIGHT) {
                finalX -= doubleNudgeX;
            }
        }

        return new ResolvedLockPlacement(finalX, baseOffsetY, baseOffsetZ, rotX, rotY, rotZ, scale);
    }

    public static ResolvedLockPlacement resolveGeneric(LockRenderProfile profile) {
        return resolveGeneric(null, profile);
    }

    public static ResolvedLockPlacement resolveGeneric(BlockState state, LockRenderProfile profile) {
        boolean useProfile = profile != null && profile.isValid();
        LockTransform fallback = LockTransform.genericDefault();

        double baseOffsetX = useProfile ? profile.offsetX : fallback.offsetX();
        double baseOffsetY = useProfile ? profile.offsetY : fallback.offsetY();
        double baseOffsetZ = useProfile ? profile.offsetZ : fallback.offsetZ();

        if (isWallAttachedFaceBlock(state)) {
            baseOffsetZ = -baseOffsetZ;
        }

        float rotX = useProfile ? profile.rotX : fallback.rotX();
        float rotY = useProfile ? profile.rotY : fallback.rotY();
        float rotZ = useProfile ? profile.rotZ : fallback.rotZ();
        float scale = useProfile ? profile.scale : fallback.scale();

        return new ResolvedLockPlacement(baseOffsetX, baseOffsetY, baseOffsetZ, rotX, rotY, rotZ, scale);
    }

    private static boolean isWallAttachedFaceBlock(BlockState state) {
        try {
            return state != null
                    && state.hasProperty(BlockStateProperties.ATTACH_FACE)
                    && state.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.WALL;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public record ResolvedLockPlacement(
            double offsetX,
            double offsetY,
            double offsetZ,
            float rotX,
            float rotY,
            float rotZ,
            float scale
    ) {
    }
}
