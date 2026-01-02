// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/SyncDoorLocksPayload.java
package org.z2six.locksmith.network;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.ClientDoorLockState;

public record SyncDoorLocksPayload(LongList positions) implements CustomPacketPayload {

    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_door_locks");
    public static final Type<SyncDoorLocksPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, SyncDoorLocksPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        LongList list = (msg == null || msg.positions == null) ? new LongArrayList() : msg.positions;
                        buf.writeVarInt(list.size());
                        for (int i = 0; i < list.size(); i++) {
                            buf.writeLong(list.getLong(i));
                        }
                    },
                    buf -> {
                        int n = buf.readVarInt();
                        LongArrayList list = new LongArrayList(n);
                        for (int i = 0; i < n; i++) {
                            list.add(buf.readLong());
                        }
                        return new SyncDoorLocksPayload(list);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncDoorLocksPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> {
                try {
                    if (msg == null || msg.positions == null) return;
                    ClientDoorLockState.setAll(msg.positions);
                    LOG.debug("[Locksmith][Client] SyncDoorLocksPayload applied. count={}", msg.positions.size());
                } catch (Throwable t) {
                    LOG.error("[Locksmith][Client] SyncDoorLocksPayload apply failed (non-fatal).", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[Locksmith][SyncDoorLocksPayload] handle enqueueWork failed (non-fatal).", t);
        }
    }
}
