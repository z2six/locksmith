package org.z2six.locksmith.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.lock.LockToggleHandler;

public record ToggleLockPayload(long posLong) implements CustomPacketPayload {
    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "toggle_lock");
    public static final Type<ToggleLockPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ToggleLockPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> buf.writeLong(msg.posLong),
                    buf -> new ToggleLockPayload(buf.readLong())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleLockPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> {
                if (msg == null || !(ctx.player() instanceof ServerPlayer player)) return;
                if (!player.isShiftKeyDown()) return;
                ServerLevel level = player.serverLevel();
                LockToggleHandler.tryToggle(level, player, BlockPos.of(msg.posLong));
            });
        } catch (Throwable t) {
            LOG.error("[Locksmith][ToggleLockPayload] handle failed (non-fatal).", t);
        }
    }
}
