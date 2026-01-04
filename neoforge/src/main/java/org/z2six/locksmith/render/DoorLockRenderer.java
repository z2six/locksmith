// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/DoorLockRenderer.java
package org.z2six.locksmith.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.registry.ModItems;
import org.z2six.locksmith.render.profile.ClientLockRenderProfiles;
import org.z2six.locksmith.render.profile.LockRenderProfile;
import org.z2six.locksmith.render.profile.LockTargetType;

import java.lang.reflect.Method;

public final class DoorLockRenderer {

    private static final Logger LOG = Constants.LOG;

    /** Used only to throttle debug logging. */
    private static long LAST_DEBUG_AT_TICK = Long.MIN_VALUE;

    // ------------------------------------------------------------------------
    // Chest render defaults (your “perfect” single-chest settings)
    // ------------------------------------------------------------------------

    private static final double CHEST_SINGLE_OFFSET_X = -0.025D;
    private static final double CHEST_SINGLE_OFFSET_Y = 0.05D;
    private static final double CHEST_SINGLE_OFFSET_Z = 0.45D;

    private static final float CHEST_SINGLE_ROT_X = 0.0F;
    private static final float CHEST_SINGLE_ROT_Y = 180.0F;
    private static final float CHEST_SINGLE_ROT_Z = 0.0F;

    private static final float CHEST_SINGLE_SCALE = 0.75F;

    /**
     * Fallback nudge for double chests when no server profile exists.
     * (Server profiles override this via profile.hingeNudgeLeft for CHEST entries.)
     */
    private static final double CHEST_DOUBLE_SHIFT_FALLBACK_X = 0.18D;

    private DoorLockRenderer() {
    }

    // ------------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------------

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

            // Base lock item; we will clone this and optionally add CustomModelData for unlocked frames.
            final ItemStack baseLockStack = new ItemStack(ModItems.LOCK_IRON.get());

            int rendered = 0;
            boolean doDebugThisTick = false;
            long nowTick = 0L;

            try {
                nowTick = level.getGameTime();
                if (LOG.isDebugEnabled() && (nowTick % 100L == 0L) && LAST_DEBUG_AT_TICK != nowTick) {
                    LAST_DEBUG_AT_TICK = nowTick;
                    doDebugThisTick = true;
                }
            } catch (Throwable ignored) {
            }

            LongSet lockedPositions = buildLockedPositionsUnion();
            LongIterator it = lockedPositions.iterator();

            while (it.hasNext()) {
                long posLong = it.nextLong();

                BlockPos pos;
                try {
                    pos = BlockPos.of(posLong);
                } catch (Throwable t) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[Locksmith][DoorLockRenderer] Invalid posLong={} (non-fatal).", posLong, t);
                    }
                    continue;
                }

                BlockState state;
                try {
                    state = level.getBlockState(pos);
                } catch (Throwable t) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[Locksmith][DoorLockRenderer] Failed to get BlockState at {} (non-fatal).", pos, t);
                    }
                    continue;
                }

                Block block = state.getBlock();
                if (!(block instanceof DoorBlock) && !(block instanceof ChestBlock)) {
                    // Only doors and chests for now.
                    continue;
                }

                ResourceLocation blockId = null;
                try {
                    blockId = BuiltInRegistries.BLOCK.getKey(block);
                } catch (Throwable ignored) {
                }

                boolean renderedThis = false;

                if (block instanceof DoorBlock) {
                    renderedThis = renderDoorLock(
                            mc,
                            level,
                            buffer,
                            pose,
                            state,
                            pos,
                            posLong,
                            blockId,
                            camX,
                            camY,
                            camZ,
                            baseLockStack,
                            nowTick,
                            doDebugThisTick
                    );
                } else if (block instanceof ChestBlock) {
                    renderedThis = renderChestLock(
                            mc,
                            level,
                            buffer,
                            pose,
                            state,
                            pos,
                            posLong,
                            blockId,
                            camX,
                            camY,
                            camZ,
                            baseLockStack,
                            nowTick,
                            doDebugThisTick
                    );
                }

                if (renderedThis) {
                    rendered++;
                }
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

    // ------------------------------------------------------------------------
    // Build union of door + chest client lock positions
    // ------------------------------------------------------------------------

    private static LongSet buildLockedPositionsUnion() {
        LongOpenHashSet set = new LongOpenHashSet();
        try {
            Long2ObjectMap<String> doorMap = org.z2six.locksmith.render.ClientDoorLockState.getSnapshot();
            if (doorMap != null && !doorMap.isEmpty()) {
                set.addAll(doorMap.keySet());
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockRenderer] Failed to read ClientDoorLockState snapshot (non-fatal).", t);
        }

        // Chest client state is kept separate; we load it reflectively so this file compiles
        // even if the class name ever changes.
        try {
            Class<?> chestCls = Class.forName("org.z2six.locksmith.render.ClientChestLockState");
            Method m = chestCls.getMethod("getSnapshot");
            Object res = m.invoke(null);
            if (res instanceof Long2ObjectMap<?> chestMap && !chestMap.isEmpty()) {
                Long2ObjectMap<?> map = chestMap;
                set.addAll(map.keySet());
            }
        } catch (ClassNotFoundException ignored) {
            // No chest client state class present – older build, or feature removed; non-fatal.
        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockRenderer] Failed to read ClientChestLockState snapshot (non-fatal).", t);
        }

        return set;
    }

    // ------------------------------------------------------------------------
    // Doors
    // ------------------------------------------------------------------------

    private static boolean renderDoorLock(
            Minecraft mc,
            Level level,
            MultiBufferSource.BufferSource buffer,
            PoseStack pose,
            BlockState state,
            BlockPos pos,
            long posLong,
            ResourceLocation blockId,
            double camX,
            double camY,
            double camZ,
            ItemStack baseLockStack,
            long nowTick,
            boolean doDebugThisTick
    ) {
        try {
            boolean open;
            Direction facing;
            DoorHingeSide hinge;

            try {
                open = state.getValue(DoorBlock.OPEN);
                facing = state.getValue(DoorBlock.FACING);
                hinge = state.getValue(DoorBlock.HINGE);
            } catch (Throwable t) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][DoorLockRenderer] Door missing expected properties at {} (non-fatal).", pos, t);
                }
                return false;
            }

            // NEW: Two-way fade logic (open fade-out AND close fade-in)
            int frameIndex = DoorLockFadeState.getFrameIndex(posLong, open, nowTick);

            ItemStack toRender;
            float alpha = 1.0F;
            boolean useAlphaWrapper = false;

            if (frameIndex == -2) {
                // Open + finished fade-out: do not render
                return false;
            } else if (frameIndex == -1) {
                // Closed steady-state (locked model)
                toRender = baseLockStack.copy();
            } else {
                // Render unlocked frames (opening or closing)
                toRender = baseLockStack.copy();
                applyCustomModelData(toRender, frameIndex);

                alpha = DoorLockFadeState.getAlpha(posLong, open, nowTick);
                if (alpha < 0.999F) {
                    useAlphaWrapper = true;
                }
            }

            // Distance culling
            double dx = (pos.getX() + 0.5D) - camX;
            double dy = (pos.getY() + 0.5D) - camY;
            double dz = (pos.getZ() + 0.5D) - camZ;
            double dist2 = dx * dx + dy * dy + dz * dz;
            if (dist2 > (128.0D * 128.0D)) {
                return false;
            }

            // Profile (if present) supplies offsets/rotations/hinge nudges.
            LockRenderProfile profile = null;
            try {
                if (blockId != null) {
                    profile = ClientLockRenderProfiles.get(blockId);
                }
            } catch (Throwable t) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][DoorLockRenderer] Failed to read profile for blockId={} (non-fatal).", blockId, t);
                }
            }

            boolean useProfile = profile != null && profile.isValid()
                    && (profile.type == null || profile.type == LockTargetType.DOOR);

            double baseOffsetX = useProfile ? profile.offsetX : LockRenderTuning.OFFSET_X;
            double baseOffsetY = useProfile ? profile.offsetY : LockRenderTuning.OFFSET_Y;
            double baseOffsetZ = useProfile ? profile.offsetZ : LockRenderTuning.OFFSET_Z;

            float rotX = useProfile ? profile.rotX : LockRenderTuning.ROT_X;
            float rotY = useProfile ? profile.rotY : LockRenderTuning.ROT_Y;
            float rotZ = useProfile ? profile.rotZ : LockRenderTuning.ROT_Z;

            float scale = useProfile ? profile.scale : LockRenderTuning.SCALE;

            double hingeLeftMag = useProfile ? profile.hingeNudgeLeft : LockRenderTuning.NUDGE_HINGE_LEFT;
            double hingeRightMag = useProfile ? profile.hingeNudgeRight : LockRenderTuning.NUDGE_HINGE_RIGHT;
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
                    pos.getX() - camX + 0.5D,
                    pos.getY() - camY + 0.5D,
                    pos.getZ() - camZ + 0.5D
            );

            float yRot = -facing.toYRot();
            pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(yRot)));

            if (doDebugThisTick) {
                LOG.debug(
                        "[Locksmith][DoorLockRenderer] DOOR pos={} blockId={} prof={} facing={} hinge={} open={} frame={} alpha={} yRot={} baseX={} nudge={} finalX={} finalY={} finalZ={} profilesCached={}",
                        pos,
                        (blockId == null ? "<null>" : blockId),
                        (useProfile ? "YES" : "NO"),
                        facing,
                        hinge,
                        open,
                        frameIndex,
                        alpha,
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

            if (rotX != 0.0F) pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(rotX)));
            if (rotY != 0.0F) pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(rotY)));
            if (rotZ != 0.0F) pose.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(rotZ)));

            pose.scale(scale, scale, scale);

            MultiBufferSource usedBuffer = buffer;
            if (useAlphaWrapper) {
                usedBuffer = new AlphaMultiBufferSource(buffer, alpha);
            }

            try {
                mc.getItemRenderer().renderStatic(
                        toRender,
                        ItemDisplayContext.FIXED,
                        light,
                        OverlayTexture.NO_OVERLAY,
                        pose,
                        usedBuffer,
                        level,
                        0
                );
            } catch (Throwable t) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][DoorLockRenderer] renderStatic failed for door pos={} (non-fatal).", pos, t);
                }
            }

            pose.popPose();
            return true;
        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockRenderer] renderDoorLock failed (non-fatal). posLong={}", posLong, t);
            return false;
        }
    }

    // ------------------------------------------------------------------------
    // Chests
    // ------------------------------------------------------------------------

    private static boolean renderChestLock(
            Minecraft mc,
            Level level,
            MultiBufferSource.BufferSource buffer,
            PoseStack pose,
            BlockState state,
            BlockPos pos,
            long posLong,
            ResourceLocation blockId,
            double camX,
            double camY,
            double camZ,
            ItemStack baseLockStack,
            long nowTick,
            boolean doDebugThisTick
    ) {
        try {
            // Determine if the chest is open via its block entity.
            boolean open = false;
            try {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof ChestBlockEntity chestBe) {
                    float openness = chestBe.getOpenNess(0.0F);
                    open = openness > 0.0F;
                }
            } catch (Throwable t) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][DoorLockRenderer] Chest open-ness check failed at {} (non-fatal).", pos, t);
                }
            }

            // NEW: Two-way fade logic (open fade-out AND close fade-in)
            int frameIndex = DoorLockFadeState.getFrameIndex(posLong, open, nowTick);

            ItemStack toRender;
            float alpha = 1.0F;
            boolean useAlphaWrapper = false;

            if (frameIndex == -2) {
                // Open + finished fade-out: do not render
                return false;
            } else if (frameIndex == -1) {
                // Closed steady-state (locked model)
                toRender = baseLockStack.copy();
            } else {
                // Render unlocked frames (opening or closing)
                toRender = baseLockStack.copy();
                applyCustomModelData(toRender, frameIndex);

                alpha = DoorLockFadeState.getAlpha(posLong, open, nowTick);
                if (alpha < 0.999F) {
                    useAlphaWrapper = true;
                }
            }

            // Distance culling
            double dx = (pos.getX() + 0.5D) - camX;
            double dy = (pos.getY() + 0.5D) - camY;
            double dz = (pos.getZ() + 0.5D) - camZ;
            double dist2 = dx * dx + dy * dy + dz * dz;
            if (dist2 > (128.0D * 128.0D)) {
                return false;
            }

            Direction facing;
            ChestType type;
            try {
                facing = state.getValue(ChestBlock.FACING);
                type = state.getValue(ChestBlock.TYPE);
            } catch (Throwable t) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][DoorLockRenderer] Chest missing expected properties at {} (non-fatal).", pos, t);
                }
                return false;
            }

            // Chest profile (if present) for offsets/rotations/scale + double nudge from server.
            LockRenderProfile profile = null;
            try {
                if (blockId != null) {
                    profile = ClientLockRenderProfiles.get(blockId);
                }
            } catch (Throwable t) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][DoorLockRenderer] Failed to read chest profile for blockId={} (non-fatal).", blockId, t);
                }
            }

            boolean useProfile = profile != null && profile.isValid()
                    && (profile.type == null || profile.type == LockTargetType.CHEST);

            double baseOffsetX = useProfile ? profile.offsetX : CHEST_SINGLE_OFFSET_X;
            double baseOffsetY = useProfile ? profile.offsetY : CHEST_SINGLE_OFFSET_Y;
            double baseOffsetZ = useProfile ? profile.offsetZ : CHEST_SINGLE_OFFSET_Z;

            float rotX = useProfile ? profile.rotX : CHEST_SINGLE_ROT_X;
            float rotY = useProfile ? profile.rotY : CHEST_SINGLE_ROT_Y;
            float rotZ = useProfile ? profile.rotZ : CHEST_SINGLE_ROT_Z;

            float scale = useProfile ? profile.scale : CHEST_SINGLE_SCALE;

            // NEW: use server-authoritative double nudge (stored in hingeNudgeLeft for CHEST profiles).
            double doubleNudgeX = useProfile
                    ? profile.hingeNudgeLeft
                    : CHEST_DOUBLE_SHIFT_FALLBACK_X;

            // Double chest nudge: move toward the center seam when looking at the chest front.
            double finalX = baseOffsetX;
            if (type != ChestType.SINGLE && doubleNudgeX != 0.0D) {
                if (type == ChestType.LEFT) {
                    finalX += doubleNudgeX;
                } else if (type == ChestType.RIGHT) {
                    finalX -= doubleNudgeX;
                }
            }

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
                    pos.getX() - camX + 0.5D,
                    pos.getY() - camY + 0.5D,
                    pos.getZ() - camZ + 0.5D
            );

            float yRot = -facing.toYRot();
            pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(yRot)));

            if (doDebugThisTick) {
                LOG.debug(
                        "[Locksmith][DoorLockRenderer] CHEST pos={} blockId={} prof={} facing={} type={} open={} frame={} alpha={} yRot={} baseX={} nudgeX={} finalX={} finalY={} finalZ={} profilesCached={}",
                        pos,
                        (blockId == null ? "<null>" : blockId),
                        (useProfile ? "YES" : "NO"),
                        facing,
                        type,
                        open,
                        frameIndex,
                        alpha,
                        yRot,
                        baseOffsetX,
                        doubleNudgeX,
                        finalX,
                        finalY,
                        finalZ,
                        ClientLockRenderProfiles.size()
                );
            }

            pose.translate(finalX, finalY, finalZ);

            if (rotX != 0.0F) pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(rotX)));
            if (rotY != 0.0F) pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(rotY)));
            if (rotZ != 0.0F) pose.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(rotZ)));
            pose.scale(scale, scale, scale);

            MultiBufferSource usedBuffer = buffer;
            if (useAlphaWrapper) {
                usedBuffer = new AlphaMultiBufferSource(buffer, alpha);
            }

            try {
                mc.getItemRenderer().renderStatic(
                        toRender,
                        ItemDisplayContext.FIXED,
                        light,
                        OverlayTexture.NO_OVERLAY,
                        pose,
                        usedBuffer,
                        level,
                        0
                );
            } catch (Throwable t) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][DoorLockRenderer] renderStatic failed for chest pos={} (non-fatal).", pos, t);
                }
            }

            pose.popPose();
            return true;
        } catch (Throwable t) {
            LOG.error("[Locksmith][DoorLockRenderer] renderChestLock failed (non-fatal). posLong={}", posLong, t);
            return false;
        }
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    /**
     * Apply CustomModelData for unlocked frames (0..4) on 1.21.1.
     * Your item model overrides:
     *   custom_model_data 0..4 -> lock_iron_unlocked0..4
     */
    private static void applyCustomModelData(ItemStack stack, int frameIndex) {
        try {
            if (stack == null || stack.isEmpty()) return;
            if (frameIndex < 0) return;

            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(frameIndex));
        } catch (Throwable t) {
            LOG.warn("[Locksmith][DoorLockRenderer] applyCustomModelData failed (non-fatal). frameIndex={}", frameIndex, t);
        }
    }

    // ------------------------------------------------------------------------
    // Alpha wrappers (1.21.1 VertexConsumer API)
    // ------------------------------------------------------------------------

    private static final class AlphaMultiBufferSource implements MultiBufferSource {

        private final MultiBufferSource delegate;
        private final float alpha;

        AlphaMultiBufferSource(MultiBufferSource delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            VertexConsumer base = delegate.getBuffer(type);
            return new AlphaVertexConsumer(base, alpha);
        }
    }

    private static final class AlphaVertexConsumer implements VertexConsumer {

        private final VertexConsumer delegate;
        private final float alpha;

        AlphaVertexConsumer(VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            int newA = (int) (a * alpha);
            if (newA < 0) newA = 0;
            if (newA > 255) newA = 255;
            return delegate.setColor(r, g, b, newA);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return delegate.setUv(u, v);
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return delegate.setUv1(u, v);
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return delegate.setUv2(u, v);
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return delegate.setNormal(x, y, z);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return delegate.addVertex(x, y, z);
        }

        @Override
        public VertexConsumer setColor(float r, float g, float b, float a) {
            float newA = a * alpha;
            if (newA < 0.0F) newA = 0.0F;
            if (newA > 1.0F) newA = 1.0F;
            return delegate.setColor(r, g, b, newA);
        }

        @Override
        public VertexConsumer setColor(int argb) {
            int a = (argb >>> 24) & 0xFF;
            int newA = (int) (a * alpha);
            if (newA < 0) newA = 0;
            if (newA > 255) newA = 255;
            int newArgb = (argb & 0x00FFFFFF) | (newA << 24);
            return delegate.setColor(newArgb);
        }
    }
}
