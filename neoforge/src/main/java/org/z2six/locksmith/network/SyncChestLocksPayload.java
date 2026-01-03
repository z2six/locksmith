// neoforge/src/main/java/org/z2six/locksmith/network/SyncChestLocksPayload.java
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
import org.z2six.locksmith.render.ClientChestLockState;

public record SyncChestLocksPayload(Long2ObjectMap<String> locks) implements CustomPacketPayload {

    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_chest_locks");
    public static final Type<SyncChestLocksPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, SyncChestLocksPayload> STREAM_CODEC =
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
                        Long2ObjectOpenHashMap<String> map = new Long2ObjectOpenHashMap<>(Math.max(0, n));
                        for (int i = 0; i < n; i++) {
                            long pos = buf.readLong();
                            String hash = buf.readUtf(128);
                            if (hash != null && !hash.isBlank()) {
                                map.put(pos, hash);
                            }
                        }
                        return new SyncChestLocksPayload(map);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncChestLocksPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> {
                try {
                    if (msg == null || msg.locks == null) return;
                    ClientChestLockState.setAll(msg.locks);
                    LOG.info("[Locksmith][Client] Synced chest locks. count={}", msg.locks.size());
                } catch (Throwable t) {
                    LOG.error("[Locksmith][Client] SyncChestLocksPayload apply failed (non-fatal).", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[Locksmith][SyncChestLocksPayload] handle enqueueWork failed (non-fatal).", t);
        }
    }
}
