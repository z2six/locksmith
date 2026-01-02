// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/AddDoorLockPayload.java
package org.z2six.locksmith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.ClientDoorLockState;

public record AddDoorLockPayload(long posLong) implements CustomPacketPayload {

    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "add_door_lock");
    public static final Type<AddDoorLockPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, AddDoorLockPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> buf.writeLong(msg.posLong),
                    buf -> new AddDoorLockPayload(buf.readLong())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AddDoorLockPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> {
                try {
                    ClientDoorLockState.add(msg.posLong);
                    LOG.debug("[Locksmith][Client] AddDoorLockPayload applied. posLong={}", msg.posLong);
                } catch (Throwable t) {
                    LOG.error("[Locksmith][Client] AddDoorLockPayload apply failed (non-fatal).", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[Locksmith][AddDoorLockPayload] handle enqueueWork failed (non-fatal).", t);
        }
    }
}
