// neoforge/src/main/java/org/z2six/locksmith/network/AddChestLockPayload.java
package org.z2six.locksmith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.ClientChestLockState;

public record AddChestLockPayload(long posLong, String hash) implements CustomPacketPayload {

    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "add_chest_lock");
    public static final Type<AddChestLockPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, AddChestLockPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeLong(msg.posLong);
                        String h = (msg.hash == null) ? "" : msg.hash;
                        buf.writeUtf(h, 128);
                    },
                    buf -> new AddChestLockPayload(buf.readLong(), buf.readUtf(128))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AddChestLockPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> {
                try {
                    if (msg == null) return;
                    if (msg.hash == null || msg.hash.isBlank()) return;
                    ClientChestLockState.put(msg.posLong, msg.hash);
                    LOG.debug("[Locksmith][Client] Added chest lock. posLong={} hashLen={}", msg.posLong, msg.hash.length());
                } catch (Throwable t) {
                    LOG.error("[Locksmith][Client] AddChestLockPayload apply failed (non-fatal).", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[Locksmith][AddChestLockPayload] handle enqueueWork failed (non-fatal).", t);
        }
    }
}
