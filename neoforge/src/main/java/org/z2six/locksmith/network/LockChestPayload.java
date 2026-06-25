// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/LockChestPayload.java
package org.z2six.locksmith.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.lock.ChestLockManager;
import org.z2six.locksmith.registry.ModItems;
import org.z2six.locksmith.render.profile.LockableBlockProfileService;
import org.z2six.locksmith.world.ChestLockSavedData;

public record LockChestPayload(long chestPosLong) implements CustomPacketPayload {

    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "lock_chest");
    public static final Type<LockChestPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, LockChestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> buf.writeLong(msg.chestPosLong),
                    buf -> new LockChestPayload(buf.readLong())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LockChestPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> handleServer(msg, ctx));
        } catch (Throwable t) {
            LOG.error("[Locksmith][LockChestPayload] enqueueWork failed (non-fatal).", t);
        }
    }

    private static void handleServer(LockChestPayload msg, IPayloadContext ctx) {
        ServerPlayer player = null;
        try {
            if (msg == null) return;

            player = (ServerPlayer) ctx.player();
            if (player == null) {
                LOG.warn("[Locksmith][LockChestPayload] ctx.player() was null.");
                return;
            }

            ServerLevel level = player.serverLevel();
            BlockPos clickedPos = BlockPos.of(msg.chestPosLong);

            BlockState state = level.getBlockState(clickedPos);
            if (state == null || state.isAir()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][LockChestPayload] Target is air/invalid at {}", clickedPos);
                }
                return;
            }

            ResourceLocation blockId = null;
            try {
                blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            } catch (Throwable ignored) {
            }

            // Server-authoritative gating: only blocks configured as type=chest may be locked.
            if (!isChestTypeConfigured(blockId)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][LockChestPayload] Denied lock: blockId={} not configured as CHEST.", blockId);
                }
                return;
            }

            ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (held == null || held.isEmpty() || !(held.getItem() instanceof IronKeyItem)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][LockChestPayload] Player {} requested chest lock but main hand is not IronKeyItem.",
                            player.getName().getString());
                }
                return;
            }
            if (!held.is(ModItems.KEY_IRON.get()) || !IronKeyItem.isRegistered(held)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][LockChestPayload] Player {} requested chest lock but key is unregistered/wrong item.",
                            player.getName().getString());
                }
                return;
            }

            BlockPos chestKeyPos = ChestLockManager.normalizeChestPos(level, clickedPos, state);
            long chestKeyLong = chestKeyPos.asLong();

            ChestLockSavedData data = ChestLockSavedData.get(level);
            if (data.isLockedLong(chestKeyLong)) {
                // Already locked: do nothing; client tried to lock again.
                return;
            }

            boolean added = ChestLockManager.tryLockChestWithHeldKey(level, player, chestKeyPos, held);
            if (!added) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][LockChestPayload] tryLockChestWithHeldKey returned false at {} for {}.",
                            chestKeyPos, player.getName().getString());
                }
                return;
            }

            String hash = data.getHashLong(chestKeyLong);
            for (ServerPlayer other : level.players()) {
                PacketDistributor.sendToPlayer(other, new AddChestLockPayload(chestKeyLong, hash));
            }

            // ✅ Dedicated-safe HUD message: S2C
            try {
                PacketDistributor.sendToPlayer(player, new HudMessagePayload(HudMessagePayload.CHEST_LOCK_SUCCESS));
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][LockChestPayload] Sent CHEST_LOCK_SUCCESS HUD payload to {}.", player.getName().getString());
                }
            } catch (Throwable t) {
                LOG.warn("[Locksmith][LockChestPayload] Failed sending CHEST_LOCK_SUCCESS HUD payload (non-fatal).", t);
            }

            LOG.info("[Locksmith] Chest locked at {} via LockChestPayload by player={}.",
                    chestKeyPos, player.getName().getString());

        } catch (Throwable t) {
            LOG.error("[Locksmith][LockChestPayload] Server handler failed (non-fatal). player={}",
                    (player == null ? "null" : player.getName().getString()), t);
        }
    }

    private static boolean isChestTypeConfigured(ResourceLocation blockId) {
        try {
            if (blockId == null) return false;

            return LockableBlockProfileService.isLockableChest(blockId);
        } catch (Throwable t) {
            LOG.warn("[Locksmith][LockChestPayload] isChestTypeConfigured failed (non-fatal). blockId={}", blockId, t);
            return false;
        }
    }
}
