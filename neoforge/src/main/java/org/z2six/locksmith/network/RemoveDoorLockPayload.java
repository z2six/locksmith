// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/RemoveDoorLockPayload.java
package org.z2six.locksmith.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.ClientDoorLockState;

/**
 * S2C: remove a door lock from client cache so the lock model stops rendering.
 */
public record RemoveDoorLockPayload(long posLong) implements CustomPacketPayload {

    private static final Logger LOG = LogUtils.getLogger();

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "remove_door_lock");
    public static final Type<RemoveDoorLockPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, RemoveDoorLockPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> buf.writeLong(msg.posLong),
                    buf -> new RemoveDoorLockPayload(buf.readLong())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RemoveDoorLockPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> {
                try {
                    if (msg == null) return;
                    ClientDoorLockState.remove(msg.posLong);
                    LOG.debug("[Locksmith][Client] Removed door lock. posLong={}", msg.posLong);
                } catch (Throwable t) {
                    LOG.error("[Locksmith][Client] RemoveDoorLockPayload apply failed (non-fatal).", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[Locksmith][RemoveDoorLockPayload] handle enqueueWork failed (non-fatal).", t);
        }
    }
}
