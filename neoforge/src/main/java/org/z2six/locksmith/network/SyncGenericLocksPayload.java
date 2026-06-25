// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/SyncGenericLocksPayload.java
package org.z2six.locksmith.network;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.ClientGenericLockState;

public record SyncGenericLocksPayload(Long2ObjectMap<String> locks) implements CustomPacketPayload {
    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_generic_locks");
    public static final Type<SyncGenericLocksPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, SyncGenericLocksPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        Long2ObjectMap<String> map = msg == null || msg.locks == null ? new Long2ObjectOpenHashMap<>() : msg.locks;
                        buf.writeVarInt(map.size());
                        for (Long2ObjectMap.Entry<String> e : map.long2ObjectEntrySet()) {
                            buf.writeLong(e.getLongKey());
                            buf.writeUtf(e.getValue() == null ? "" : e.getValue(), 128);
                        }
                    },
                    buf -> {
                        int n = buf.readVarInt();
                        Long2ObjectOpenHashMap<String> map = new Long2ObjectOpenHashMap<>(Math.max(0, n));
                        for (int i = 0; i < n; i++) {
                            long pos = buf.readLong();
                            String hash = buf.readUtf(128);
                            if (hash != null && !hash.isBlank()) map.put(pos, hash);
                        }
                        return new SyncGenericLocksPayload(map);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncGenericLocksPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> {
                if (msg != null && msg.locks != null) {
                    ClientGenericLockState.setAll(msg.locks);
                    LOG.info("[Locksmith][Client] Synced generic locks. count={}", msg.locks.size());
                }
            });
        } catch (Throwable t) {
            LOG.error("[Locksmith][SyncGenericLocksPayload] handle failed (non-fatal).", t);
        }
    }
}
