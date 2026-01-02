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

            for (long posLong : ClientDoorLockState.getSnapshot().keySet()) {
                BlockPos pos = BlockPos.of(posLong);

                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof DoorBlock)) {
                    continue;
                }

                boolean open = state.getValue(DoorBlock.OPEN);
                if (open) {
                    continue;
                }

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

                pose.translate(pos.getX() - camX + 0.5, pos.getY() - camY + 0.5, pos.getZ() - camZ + 0.5);

                float yRot = -facing.toYRot();
                pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(yRot)));

                double hingeSign = (hinge == DoorHingeSide.LEFT) ? -1.0 : 1.0;

                pose.translate(
                        LockRenderTuning.OFFSET_X + hingeSign * LockRenderTuning.HINGE_NUDGE,
                        LockRenderTuning.OFFSET_Y,
                        LockRenderTuning.OFFSET_Z
                );

                if (LockRenderTuning.ROT_X != 0) pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(LockRenderTuning.ROT_X)));
                if (LockRenderTuning.ROT_Y != 0) pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(LockRenderTuning.ROT_Y)));
                if (LockRenderTuning.ROT_Z != 0) pose.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(LockRenderTuning.ROT_Z)));

                pose.scale(LockRenderTuning.SCALE, LockRenderTuning.SCALE, LockRenderTuning.SCALE);

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

            buffer.endBatch();

            if (rendered > 0 && (mc.level.getGameTime() % 200 == 0)) {
                LOG.debug("[Locksmith][DoorLockRenderer] Rendered {} lock(s) this stage.", rendered);
            }

        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockRenderer] onRenderLevelStage failed (non-fatal).", t);
        }
    }
}
