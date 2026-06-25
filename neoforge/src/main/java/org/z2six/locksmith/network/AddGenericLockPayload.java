// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/AddGenericLockPayload.java
package org.z2six.locksmith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.ClientGenericLockState;

public record AddGenericLockPayload(long posLong, String hash) implements CustomPacketPayload {
    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "add_generic_lock");
    public static final Type<AddGenericLockPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, AddGenericLockPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeLong(msg.posLong);
                        buf.writeUtf(msg.hash == null ? "" : msg.hash, 128);
                    },
                    buf -> new AddGenericLockPayload(buf.readLong(), buf.readUtf(128))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AddGenericLockPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> {
                if (msg != null && msg.hash != null && !msg.hash.isBlank()) {
                    ClientGenericLockState.put(msg.posLong, msg.hash);
                }
            });
        } catch (Throwable t) {
            LOG.error("[Locksmith][AddGenericLockPayload] handle failed (non-fatal).", t);
        }
    }
}
