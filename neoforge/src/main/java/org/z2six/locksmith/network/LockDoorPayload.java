// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/LockDoorPayload.java
package org.z2six.locksmith.network;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.lock.DoorLockManager;
import org.z2six.locksmith.world.DoorLockSavedData;

public record LockDoorPayload(long doorPosLong) implements CustomPacketPayload {

    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "lock_door");
    public static final Type<LockDoorPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, LockDoorPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> buf.writeLong(msg.doorPosLong),
                    buf -> new LockDoorPayload(buf.readLong())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LockDoorPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> handleServer(msg, ctx));
        } catch (Throwable t) {
            LOG.error("[Locksmith][LockDoorPayload] enqueueWork failed (non-fatal).", t);
        }
    }

    private static void handleServer(LockDoorPayload msg, IPayloadContext ctx) {
        ServerPlayer player = null;
        try {
            if (msg == null) return;

            player = (ServerPlayer) ctx.player();
            if (player == null) {
                LOG.warn("[Locksmith][LockDoorPayload] ctx.player() was null.");
                return;
            }

            ServerLevel level = player.serverLevel();
            BlockPos pos = BlockPos.of(msg.doorPosLong);

            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][LockDoorPayload] Target not a door: {} state={}", pos, state.getBlock());
                }
                return;
            }

            ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (held == null || held.isEmpty() || !(held.getItem() instanceof IronKeyItem)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][LockDoorPayload] Player {} requested lock but main hand is not IronKeyItem.",
                            player.getName().getString());
                }
                return;
            }
            if (!IronKeyItem.isRegistered(held)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][LockDoorPayload] Player {} requested lock but key is unregistered.",
                            player.getName().getString());
                }
                return;
            }

            BlockPos doorPos = DoorLockManager.normalizeDoorPos(level, pos, state);

            DoorLockSavedData data = DoorLockSavedData.get(level);
            if (data.isLocked(doorPos)) {
                // Already locked: still slam shut defensively.
                DoorLockManager.forceCloseDoor(level, doorPos);
                DoorLockManager.requestForceClose(level, doorPos, 5);
                return;
            }

            boolean added = DoorLockManager.tryLockDoorWithHeldKey(level, player, doorPos, held);

            // Slam shut now and for a few ticks.
            DoorLockManager.forceCloseDoor(level, doorPos);
            DoorLockManager.requestForceClose(level, doorPos, 5);

            if (!added) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][LockDoorPayload] tryLockDoorWithHeldKey returned false at {} for {}.",
                            doorPos, player.getName().getString());
                }
                return;
            }

            String hash = data.getHash(doorPos);
            for (ServerPlayer other : level.players()) {
                PacketDistributor.sendToPlayer(other, new AddDoorLockPayload(doorPos.asLong(), hash));
            }

            // ✅ Dedicated-safe HUD message: S2C
            try {
                PacketDistributor.sendToPlayer(player, new HudMessagePayload(HudMessagePayload.DOOR_LOCK_SUCCESS));
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][LockDoorPayload] Sent DOOR_LOCK_SUCCESS HUD payload to {}.", player.getName().getString());
                }
            } catch (Throwable t) {
                LOG.warn("[Locksmith][LockDoorPayload] Failed sending DOOR_LOCK_SUCCESS HUD payload (non-fatal).", t);
            }

            LOG.info("[Locksmith] Door locked at {} via LockDoorPayload by player={} (forced-close queued).",
                    doorPos, player.getName().getString());

        } catch (Throwable t) {
            LOG.error("[Locksmith][LockDoorPayload] Server handler failed (non-fatal). player={}",
                    (player == null ? "null" : player.getName().getString()), t);
        }
    }
}
