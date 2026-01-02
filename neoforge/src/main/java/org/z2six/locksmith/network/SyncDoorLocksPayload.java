// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/SyncDoorLocksPayload.java
package org.z2six.locksmith.network;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.ClientDoorLockState;

/**
 * S2C: full sync of locked doors for current dimension.
 * Sends posLong -> requiredHash so client can prevent door-open prediction flicker.
 */
public record SyncDoorLocksPayload(Long2ObjectMap<String> locks) implements CustomPacketPayload {

    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_door_locks");
    public static final Type<SyncDoorLocksPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, SyncDoorLocksPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        Long2ObjectMap<String> map = (msg == null || msg.locks == null) ? new Long2ObjectOpenHashMap<>() : msg.locks;
                        buf.writeVarInt(map.size());
                        for (Long2ObjectMap.Entry<String> e : map.long2ObjectEntrySet()) {
                            buf.writeLong(e.getLongKey());
                            String hash = e.getValue();
                            if (hash == null) hash = "";
                            buf.writeUtf(hash, 128);
                        }
                    },
                    buf -> {
                        int n = buf.readVarInt();
                        Long2ObjectOpenHashMap<String> map = new Long2ObjectOpenHashMap<>(n);
                        for (int i = 0; i < n; i++) {
                            long pos = buf.readLong();
                            String hash = buf.readUtf(128);
                            if (hash != null && !hash.isBlank()) {
                                map.put(pos, hash);
                            }
                        }
                        return new SyncDoorLocksPayload(map);
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
                    if (msg == null || msg.locks == null) return;
                    ClientDoorLockState.setAll(msg.locks);
                    LOG.info("[Locksmith][Client] Synced door locks. count={}", msg.locks.size());
                } catch (Throwable t) {
                    LOG.error("[Locksmith][Client] SyncDoorLocksPayload apply failed (non-fatal).", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[Locksmith][SyncDoorLocksPayload] handle enqueueWork failed (non-fatal).", t);
        }
    }
}
