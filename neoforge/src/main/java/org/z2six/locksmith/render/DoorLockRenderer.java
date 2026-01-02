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

public final class DoorLockRenderer {

    private static final Logger LOG = Constants.LOG;

    /**
     * Debug throttling (avoid log spam)
     */
    private static long LAST_DEBUG_AT_TICK = Long.MIN_VALUE;

    private DoorLockRenderer() {
        // no-op
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        try {
            if (event == null) return;

            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null || mc.player == null) {
                return;
            }

            Level level = mc.level;

            MultiBufferSource.BufferSource buffer;
            try {
                buffer = mc.renderBuffers().bufferSource();
            } catch (Throwable t) {
                LOG.debug("[Locksmith][DoorLockRenderer] Failed to get bufferSource (non-fatal).", t);
                return;
            }

            PoseStack pose = event.getPoseStack();
            if (pose == null) return;

            double camX = event.getCamera().getPosition().x;
            double camY = event.getCamera().getPosition().y;
            double camZ = event.getCamera().getPosition().z;

            ItemStack lockStack = new ItemStack(ModItems.LOCK_IRON.get());
            int rendered = 0;

            boolean doDebugThisTick = false;
            try {
                long nowTick = mc.level.getGameTime();
                if (LOG.isDebugEnabled() && (nowTick % 100 == 0) && LAST_DEBUG_AT_TICK != nowTick) {
                    LAST_DEBUG_AT_TICK = nowTick;
                    doDebugThisTick = true;
                }
            } catch (Throwable ignored) {
                // ignore
            }

            for (long posLong : ClientDoorLockState.getSnapshot().keySet()) {
                BlockPos pos;
                try {
                    pos = BlockPos.of(posLong);
                } catch (Throwable t) {
                    continue;
                }

                BlockState state;
                try {
                    state = level.getBlockState(pos);
                } catch (Throwable t) {
                    continue;
                }

                if (!(state.getBlock() instanceof DoorBlock)) {
                    continue;
                }

                boolean open;
                try {
                    open = state.getValue(DoorBlock.OPEN);
                } catch (Throwable t) {
                    continue;
                }
                if (open) {
                    continue;
                }

                // Distance cull
                double dx = (pos.getX() + 0.5) - camX;
                double dy = (pos.getY() + 0.5) - camY;
                double dz = (pos.getZ() + 0.5) - camZ;
                double dist2 = dx * dx + dy * dy + dz * dz;
                if (dist2 > (128.0 * 128.0)) {
                    continue;
                }

                Direction facing;
                DoorHingeSide hinge;
                try {
                    facing = state.getValue(DoorBlock.FACING);
                    hinge = state.getValue(DoorBlock.HINGE);
                } catch (Throwable t) {
                    continue;
                }

                int light;
                try {
                    light = LevelRenderer.getLightColor(level, pos);
                } catch (Throwable t) {
                    light = 0x00F000F0; // safe-ish fallback
                }

                pose.pushPose();

                // Move to block center relative to camera
                pose.translate(
                        pos.getX() - camX + 0.5,
                        pos.getY() - camY + 0.5,
                        pos.getZ() - camZ + 0.5
                );

                // Rotate so our local frame matches door facing
                float yRot = -facing.toYRot();
                pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(yRot)));

                // Apply hinge compensation with opposite signs, but different magnitudes.
                // LEFT hinge must move opposite direction across the face vs RIGHT hinge.
                double hingeSignedNudge;
                if (hinge == DoorHingeSide.LEFT) {
                    hingeSignedNudge = -LockRenderTuning.NUDGE_HINGE_LEFT;
                } else {
                    hingeSignedNudge = LockRenderTuning.NUDGE_HINGE_RIGHT;
                }

                double finalX = LockRenderTuning.OFFSET_X + hingeSignedNudge;
                double finalY = LockRenderTuning.OFFSET_Y;
                double finalZ = LockRenderTuning.OFFSET_Z;

                if (doDebugThisTick) {
                    LOG.debug(
                            "[Locksmith][DoorLockRenderer] Render lock pos={} facing={} hinge={} yRot={} baseX={} nudge={} finalX={} finalY={} finalZ={}",
                            pos, facing, hinge, yRot, LockRenderTuning.OFFSET_X, hingeSignedNudge, finalX, finalY, finalZ
                    );
                }

                pose.translate(finalX, finalY, finalZ);

                // Optional additional rotations
                if (LockRenderTuning.ROT_X != 0) {
                    pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(LockRenderTuning.ROT_X)));
                }
                if (LockRenderTuning.ROT_Y != 0) {
                    pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(LockRenderTuning.ROT_Y)));
                }
                if (LockRenderTuning.ROT_Z != 0) {
                    pose.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(LockRenderTuning.ROT_Z)));
                }

                // Scale
                pose.scale(LockRenderTuning.SCALE, LockRenderTuning.SCALE, LockRenderTuning.SCALE);

                try {
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
                } catch (Throwable t) {
                    // Don’t crash render loop; just skip this lock.
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[Locksmith][DoorLockRenderer] renderStatic failed for pos={} (non-fatal).", pos, t);
                    }
                }

                pose.popPose();
                rendered++;
            }

            try {
                buffer.endBatch();
            } catch (Throwable t) {
                LOG.debug("[Locksmith][DoorLockRenderer] buffer.endBatch failed (non-fatal).", t);
            }

            if (rendered > 0 && doDebugThisTick) {
                LOG.debug("[Locksmith][DoorLockRenderer] Rendered {} lock(s) this stage.", rendered);
            }

        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockRenderer] onRenderLevelStage failed (non-fatal).", t);
        }
    }
}
