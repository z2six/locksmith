// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/DoorLockRenderer.java
package org.z2six.locksmith.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.registry.ModItems;
import org.z2six.locksmith.render.profile.ClientLockRenderProfiles;
import org.z2six.locksmith.render.profile.LockRenderProfile;
import org.z2six.locksmith.render.profile.LockTargetType;

public final class DoorLockRenderer {

    private static final Logger LOG = Constants.LOG;

    private static long LAST_DEBUG_AT_TICK = Long.MIN_VALUE;

    private DoorLockRenderer() {
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

            var pose = event.getPoseStack();
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

                // --- NEW: profile lookup by block id (server-authoritative, S2C synced) ---
                ResourceLocation blockId = null;
                try {
                    blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                } catch (Throwable ignored) {
                }

                LockRenderProfile prof = null;
                if (blockId != null) {
                    prof = ClientLockRenderProfiles.get(blockId);
                }

                // Only apply profile if it's a DOOR profile.
                boolean useProfile = (prof != null && prof.isValid() && prof.type == LockTargetType.DOOR);

                // Fallback to current hardcoded tuning if missing or wrong type.
                double baseOffsetX = useProfile ? prof.offsetX : LockRenderTuning.OFFSET_X;
                double baseOffsetY = useProfile ? prof.offsetY : LockRenderTuning.OFFSET_Y;
                double baseOffsetZ = useProfile ? prof.offsetZ : LockRenderTuning.OFFSET_Z;

                float rotX = useProfile ? prof.rotX : LockRenderTuning.ROT_X;
                float rotY = useProfile ? prof.rotY : LockRenderTuning.ROT_Y;
                float rotZ = useProfile ? prof.rotZ : LockRenderTuning.ROT_Z;

                float scale = useProfile ? prof.scale : LockRenderTuning.SCALE;

                double hingeLeftMag = useProfile ? prof.hingeNudgeLeft : LockRenderTuning.NUDGE_HINGE_LEFT;
                double hingeRightMag = useProfile ? prof.hingeNudgeRight : LockRenderTuning.NUDGE_HINGE_RIGHT;

                double hingeSignedNudge = (hinge == DoorHingeSide.LEFT) ? -hingeLeftMag : hingeRightMag;

                double finalX = baseOffsetX + hingeSignedNudge;
                double finalY = baseOffsetY;
                double finalZ = baseOffsetZ;

                int light;
                try {
                    light = LevelRenderer.getLightColor(level, pos);
                } catch (Throwable t) {
                    light = 0x00F000F0;
                }

                pose.pushPose();

                pose.translate(
                        pos.getX() - camX + 0.5,
                        pos.getY() - camY + 0.5,
                        pos.getZ() - camZ + 0.5
                );

                float yRot = -facing.toYRot();
                pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(yRot)));

                if (doDebugThisTick) {
                    LOG.debug(
                            "[Locksmith][DoorLockRenderer] pos={} blockId={} prof={} facing={} hinge={} yRot={} baseX={} nudge={} finalX={} finalY={} finalZ={} profilesCached={}",
                            pos,
                            (blockId == null ? "<null>" : blockId),
                            (useProfile ? "YES" : "NO"),
                            facing,
                            hinge,
                            yRot,
                            baseOffsetX,
                            hingeSignedNudge,
                            finalX,
                            finalY,
                            finalZ,
                            ClientLockRenderProfiles.size()
                    );
                }

                pose.translate(finalX, finalY, finalZ);

                if (rotX != 0) pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(rotX)));
                if (rotY != 0) pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(rotY)));
                if (rotZ != 0) pose.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(rotZ)));

                pose.scale(scale, scale, scale);

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
