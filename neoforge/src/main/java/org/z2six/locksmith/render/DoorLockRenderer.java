// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/DoorLockRenderer.java
package org.z2six.locksmith.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.registry.ModItems;

/**
 * Client-only door lock renderer.
 * Renders lock_iron model on locked doors that are CLOSED.
 */
public final class DoorLockRenderer {

    private static final Logger LOG = Constants.LOG;

    private DoorLockRenderer() {
        // no-op
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        try {
            // Render late so it appears on top
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null || mc.player == null) {
                return;
            }

            Level level = mc.level;

            MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
            PoseStack pose = event.getPoseStack();

            double camX = event.getCamera().getPosition().x;
            double camY = event.getCamera().getPosition().y;
            double camZ = event.getCamera().getPosition().z;

            ItemStack lockStack = new ItemStack(ModItems.LOCK_IRON.get());

            int rendered = 0;

            for (long posLong : ClientDoorLockState.getSnapshot()) {
                BlockPos pos = BlockPos.of(posLong);

                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof DoorBlock)) {
                    continue;
                }

                // Only render on CLOSED doors
                boolean open = state.getValue(DoorBlock.OPEN);
                if (open) {
                    continue;
                }

                // Optional distance cull (keeps it cheap)
                double dx = (pos.getX() + 0.5) - camX;
                double dy = (pos.getY() + 0.5) - camY;
                double dz = (pos.getZ() + 0.5) - camZ;
                double dist2 = dx * dx + dy * dy + dz * dz;
                if (dist2 > (128.0 * 128.0)) {
                    continue;
                }

                Direction facing = state.getValue(DoorBlock.FACING);
                DoorHingeSide hinge = state.getValue(DoorBlock.HINGE);

                int light = LevelRenderer.getLightColor(level, pos);

                pose.pushPose();

                // Move to door block relative to camera
                pose.translate(pos.getX() - camX + 0.5, pos.getY() - camY + 0.5, pos.getZ() - camZ + 0.5);

                // Rotate to match door facing (model faces "south" by default in many item models; we adjust)
                float yRot = -facing.toYRot();
                pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(yRot)));

                // Apply hinge nudge left/right (so you can place on correct side)
                double hingeSign = (hinge == DoorHingeSide.LEFT) ? -1.0 : 1.0;

                // Apply tuning offsets (in the rotated local space)
                pose.translate(
                        LockRenderTuning.OFFSET_X + hingeSign * LockRenderTuning.HINGE_NUDGE,
                        LockRenderTuning.OFFSET_Y,
                        LockRenderTuning.OFFSET_Z
                );

                // Apply tuning rotations
                if (LockRenderTuning.ROT_X != 0) pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(LockRenderTuning.ROT_X)));
                if (LockRenderTuning.ROT_Y != 0) pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(LockRenderTuning.ROT_Y)));
                if (LockRenderTuning.ROT_Z != 0) pose.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(LockRenderTuning.ROT_Z)));

                // Scale
                pose.scale(LockRenderTuning.SCALE, LockRenderTuning.SCALE, LockRenderTuning.SCALE);

                // Render the lock item model into the world
                mc.getItemRenderer().renderStatic(
                        lockStack,
                        ItemDisplayContext.FIXED,
                        light,
                        OverlayTexture.NO_OVERLAY,
                        pose,
                        buffer,
                        level,
                        0
                );

                pose.popPose();

                rendered++;
            }

            // Flush
            buffer.endBatch();

            if (rendered > 0 && (mc.level.getGameTime() % 200 == 0)) {
                LOG.debug("[Locksmith][DoorLockRenderer] Rendered {} lock(s) this frame stage.", rendered);
            }

        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockRenderer] onRenderLevelStage failed (non-fatal).", t);
        }
    }
}
