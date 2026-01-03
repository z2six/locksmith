// neoforge/src/main/java/org/z2six/locksmith/network/RemoveChestLockPayload.java
package org.z2six.locksmith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.ClientChestLockState;

public record RemoveChestLockPayload(long posLong) implements CustomPacketPayload {

    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "remove_chest_lock");
    public static final Type<RemoveChestLockPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, RemoveChestLockPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> buf.writeLong(msg.posLong),
                    buf -> new RemoveChestLockPayload(buf.readLong())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RemoveChestLockPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> {
                try {
                    if (msg == null) return;
                    ClientChestLockState.remove(msg.posLong);
                    LOG.debug("[Locksmith][Client] Removed chest lock. posLong={}", msg.posLong);
                } catch (Throwable t) {
                    LOG.error("[Locksmith][Client] RemoveChestLockPayload apply failed (non-fatal).", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[Locksmith][RemoveChestLockPayload] handle enqueueWork failed (non-fatal).", t);
        }
    }
}
