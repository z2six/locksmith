// MainFile: neoforge/src/main/java/org/z2six/locksmith/client/screen/LockableBlocksPreviewRenderer.java
package org.z2six.locksmith.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.z2six.locksmith.registry.ModItems;
import org.z2six.locksmith.render.LockPlacementResolver;
import org.z2six.locksmith.render.profile.LockRenderProfile;
import org.z2six.locksmith.render.profile.LockTargetType;
import org.z2six.locksmith.render.profile.LockableBlockEntry;

public final class LockableBlocksPreviewRenderer {
    private LockableBlocksPreviewRenderer() {
    }

    public static void render(GuiGraphics gfx, int x, int y, int w, int h, LockableBlockEntry entry, float yaw, float pitch, float zoom, float panX, float panY, boolean multiblockPreview) {
        if (gfx == null || entry == null || entry.blockId() == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        Block block = BuiltInRegistries.BLOCK.get(entry.blockId());
        if (block == null || !BuiltInRegistries.BLOCK.getKey(block).equals(entry.blockId())) {
            return;
        }

        boolean doorPreview = entry.type() == LockTargetType.DOOR && block instanceof DoorBlock;
        boolean doubleChestPreview = entry.type() == LockTargetType.CHEST && multiblockPreview && block instanceof ChestBlock;
        ChestType previewChestType = doubleChestPreview ? ChestType.RIGHT : ChestType.SINGLE;
        BlockState state = normalizedPreviewState(
                block.defaultBlockState(),
                block,
                false,
                previewChestType
        );
        if (entry.type() == LockTargetType.GENERIC) {
            state = normalizedGenericPreviewState(state);
        }

        float objectUnitsY = doorPreview ? 2.0F : 1.0F;
        float objectUnitsX = doubleChestPreview ? 2.0F : 1.0F;

        float fov = 50.0F;
        float cameraDistance = 5.0F;
        float visibleY = (float) (2.0D * cameraDistance * Math.tan(Math.toRadians(fov * 0.5F)));
        float visibleX = visibleY * ((float) w / Math.max(1.0F, (float) h));
        float scale = Math.max(0.2F, Math.min(visibleX / objectUnitsX, visibleY / objectUnitsY) * 0.62F * zoom);

        Window window = mc.getWindow();
        int fullViewportW = window.getWidth();
        int fullViewportH = window.getHeight();
        double guiScale = window.getGuiScale();
        int viewportX = (int) Math.floor(x * guiScale);
        int viewportY = (int) Math.floor((window.getGuiScaledHeight() - (y + h)) * guiScale);
        int viewportW = Math.max(1, (int) Math.ceil(w * guiScale));
        int viewportH = Math.max(1, (int) Math.ceil(h * guiScale));

        gfx.flush();
        gfx.enableScissor(x, y, x + w, y + h);
        PoseStack pose = new PoseStack();
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.viewport(viewportX, viewportY, viewportW, viewportH);
        RenderSystem.setProjectionMatrix(
                new Matrix4f().perspective((float) Math.toRadians(fov), (float) viewportW / (float) viewportH, 0.05F, 100.0F),
                VertexSorting.DISTANCE_TO_ORIGIN
        );
        RenderSystem.enableDepthTest();
        RenderSystem.clearDepth(1.0D);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, false);
        pose.translate(0.0D, 0.0D, -cameraDistance);
        pose.translate(panX, panY, 0.0D);
        pose.scale(scale, scale, scale);
        pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(pitch)));
        pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(yaw)));
        pose.translate(-(objectUnitsX * 0.5D), -(objectUnitsY * 0.5D), -0.5D);

        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        try {
            renderPreviewBlocks(mc, pose, buffer, state, block, doorPreview, doubleChestPreview);

            pose.pushPose();
            pose.translate(0.5D, 0.5D, 0.5D);
            LockRenderProfile profile = entry.toRenderProfile();
            LockPlacementResolver.ResolvedLockPlacement placement;
            if (entry.type() == LockTargetType.CHEST) {
                Direction facing = facingOf(state, entry.type());
                pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(-facing.toYRot())));
                placement = LockPlacementResolver.resolveChest(state, profile);
            } else if (entry.type() == LockTargetType.DOOR) {
                Direction facing = facingOf(state, entry.type());
                pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(-facing.toYRot())));
                placement = LockPlacementResolver.resolveDoor(state, profile);
            } else {
                Direction facing = genericFacingOf(state);
                if (facing != null && facing.getAxis().isHorizontal()) {
                    pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(-facing.toYRot())));
                }
                placement = LockPlacementResolver.resolveGeneric(state, profile);
            }
            pose.translate(placement.offsetX(), placement.offsetY(), placement.offsetZ());
            if (placement.rotX() != 0.0F) pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(placement.rotX())));
            if (placement.rotY() != 0.0F) pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(placement.rotY())));
            if (placement.rotZ() != 0.0F) pose.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(placement.rotZ())));
            pose.scale(placement.scale(), placement.scale(), placement.scale());
            mc.getItemRenderer().renderStatic(
                    new ItemStack(ModItems.LOCK_IRON.get()),
                    ItemDisplayContext.FIXED,
                    0x00F000F0,
                    OverlayTexture.NO_OVERLAY,
                    pose,
                    buffer,
                    mc.level,
                    0
            );
            pose.popPose();
        } finally {
            buffer.endBatch();
            RenderSystem.disableDepthTest();
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.viewport(0, 0, fullViewportW, fullViewportH);
            gfx.disableScissor();
        }
    }

    private static void renderPreviewBlocks(
            Minecraft mc,
            PoseStack pose,
            MultiBufferSource.BufferSource buffer,
            BlockState state,
            Block block,
            boolean doorPreview,
            boolean doubleChestPreview
    ) {
        if (doorPreview) {
            BlockState lower = normalizedPreviewState(state, block, false, ChestType.SINGLE);
            BlockState upper = normalizedPreviewState(state, block, true, ChestType.SINGLE);

            pose.pushPose();
            mc.getBlockRenderer().renderSingleBlock(lower, pose, buffer, 0x00F000F0, OverlayTexture.NO_OVERLAY);
            pose.popPose();

            pose.pushPose();
            pose.translate(0.0D, 1.0D, 0.0D);
            mc.getBlockRenderer().renderSingleBlock(upper, pose, buffer, 0x00F000F0, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            return;
        }

        if (doubleChestPreview) {
            BlockState right = normalizedPreviewState(state, block, false, ChestType.RIGHT);
            BlockState left = normalizedPreviewState(state, block, false, ChestType.LEFT);

            pose.pushPose();
            renderChestBlockEntity(mc, pose, buffer, right, BlockPos.ZERO);
            pose.popPose();

            pose.pushPose();
            pose.translate(1.0D, 0.0D, 0.0D);
            renderChestBlockEntity(mc, pose, buffer, left, BlockPos.ZERO.east());
            pose.popPose();
            return;
        }

        mc.getBlockRenderer().renderSingleBlock(state, pose, buffer, 0x00F000F0, OverlayTexture.NO_OVERLAY);
    }

    private static void renderChestBlockEntity(
            Minecraft mc,
            PoseStack pose,
            MultiBufferSource.BufferSource buffer,
            BlockState state,
            BlockPos pos
    ) {
        BlockEntity chest = state.is(Blocks.TRAPPED_CHEST)
                ? new TrappedChestBlockEntity(pos, state)
                : new ChestBlockEntity(pos, state);
        if (mc.level != null) {
            chest.setLevel(mc.level);
        }
        mc.getBlockEntityRenderDispatcher().renderItem(chest, pose, buffer, 0x00F000F0, OverlayTexture.NO_OVERLAY);
    }

    private static BlockState normalizedPreviewState(BlockState state, Block block, boolean upperDoorHalf, ChestType chestType) {
        BlockState out = state;
        if (block instanceof DoorBlock) {
            if (out.hasProperty(DoorBlock.FACING)) {
                out = out.setValue(DoorBlock.FACING, Direction.SOUTH);
            }
            if (out.hasProperty(DoorBlock.OPEN)) {
                out = out.setValue(DoorBlock.OPEN, false);
            }
            if (out.hasProperty(DoorBlock.HALF)) {
                out = out.setValue(DoorBlock.HALF, upperDoorHalf ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
            }
        }
        if (block instanceof ChestBlock) {
            if (out.hasProperty(ChestBlock.FACING)) {
                out = out.setValue(ChestBlock.FACING, Direction.SOUTH);
            }
            if (out.hasProperty(ChestBlock.TYPE)) {
                out = out.setValue(ChestBlock.TYPE, chestType == null ? ChestType.SINGLE : chestType);
            }
        }
        return out;
    }

    private static Direction facingOf(BlockState state, LockTargetType type) {
        try {
            if (type == LockTargetType.CHEST && state.hasProperty(ChestBlock.FACING)) {
                return state.getValue(ChestBlock.FACING);
            }
            if (type == LockTargetType.DOOR && state.hasProperty(DoorBlock.FACING)) {
                return state.getValue(DoorBlock.FACING);
            }
        } catch (Throwable ignored) {
        }
        return Direction.SOUTH;
    }

    private static BlockState normalizedGenericPreviewState(BlockState state) {
        BlockState out = state;
        try {
            if (out.hasProperty(BlockStateProperties.ATTACH_FACE)) {
                out = out.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL);
            }
            if (out.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                out = out.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
            } else if (out.hasProperty(BlockStateProperties.FACING)) {
                out = out.setValue(BlockStateProperties.FACING, Direction.SOUTH);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static Direction genericFacingOf(BlockState state) {
        try {
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            }
            if (state.hasProperty(BlockStateProperties.FACING)) {
                return state.getValue(BlockStateProperties.FACING);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
