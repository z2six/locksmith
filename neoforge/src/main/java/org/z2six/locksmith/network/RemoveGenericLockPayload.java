// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/RemoveGenericLockPayload.java
package org.z2six.locksmith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.ClientGenericLockState;

public record RemoveGenericLockPayload(long posLong) implements CustomPacketPayload {
    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "remove_generic_lock");
    public static final Type<RemoveGenericLockPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, RemoveGenericLockPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> buf.writeLong(msg.posLong),
                    buf -> new RemoveGenericLockPayload(buf.readLong())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RemoveGenericLockPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> {
                if (msg != null) ClientGenericLockState.remove(msg.posLong);
            });
        } catch (Throwable t) {
            LOG.error("[Locksmith][RemoveGenericLockPayload] handle failed (non-fatal).", t);
        }
    }
}
