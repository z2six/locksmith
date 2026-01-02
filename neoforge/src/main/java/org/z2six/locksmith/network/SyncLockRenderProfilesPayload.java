// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/SyncLockRenderProfilesPayload.java
package org.z2six.locksmith.network;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.profile.ClientLockRenderProfiles;
import org.z2six.locksmith.render.profile.LockRenderProfile;
import org.z2six.locksmith.render.profile.LockTargetType;

import java.util.Map;

/**
 * Server -> Client sync of render profiles.
 * This is independent from gameplay lock state.
 */
public record SyncLockRenderProfilesPayload(Map<ResourceLocation, LockRenderProfile> profiles) implements CustomPacketPayload {

    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_lock_render_profiles");
    public static final Type<SyncLockRenderProfilesPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, SyncLockRenderProfilesPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        Map<ResourceLocation, LockRenderProfile> map = (msg == null || msg.profiles == null)
                                ? Map.of()
                                : msg.profiles;

                        buf.writeVarInt(map.size());

                        for (Map.Entry<ResourceLocation, LockRenderProfile> e : map.entrySet()) {
                            ResourceLocation key = e.getKey();
                            LockRenderProfile p = e.getValue();

                            String keyStr = (key == null) ? "" : key.toString();
                            buf.writeUtf(keyStr, 256);

                            // If value is null, write a minimal "invalid" record; client will skip it.
                            byte typeId = (p == null || p.type == null) ? LockTargetType.DOOR.id : p.type.id;
                            buf.writeByte(typeId);

                            // offsets
                            buf.writeDouble(p == null ? 0.0 : p.offsetX);
                            buf.writeDouble(p == null ? 0.0 : p.offsetY);
                            buf.writeDouble(p == null ? 0.0 : p.offsetZ);

                            // rots
                            buf.writeFloat(p == null ? 0.0f : p.rotX);
                            buf.writeFloat(p == null ? 0.0f : p.rotY);
                            buf.writeFloat(p == null ? 0.0f : p.rotZ);

                            buf.writeFloat(p == null ? 1.0f : p.scale);

                            // door-specific fields (safe to keep for future)
                            buf.writeDouble(p == null ? 0.0 : p.hingeNudgeLeft);
                            buf.writeDouble(p == null ? 0.0 : p.hingeNudgeRight);
                        }
                    },
                    buf -> {
                        int n = buf.readVarInt();
                        Object2ObjectOpenHashMap<ResourceLocation, LockRenderProfile> out = new Object2ObjectOpenHashMap<>(Math.max(0, n));

                        for (int i = 0; i < n; i++) {
                            String keyStr = buf.readUtf(256);
                            ResourceLocation key = ResourceLocation.tryParse(keyStr);

                            byte typeId = buf.readByte();
                            LockTargetType type = LockTargetType.fromId(typeId);

                            double ox = buf.readDouble();
                            double oy = buf.readDouble();
                            double oz = buf.readDouble();

                            float rx = buf.readFloat();
                            float ry = buf.readFloat();
                            float rz = buf.readFloat();

                            float scale = buf.readFloat();

                            double hingeLeft = buf.readDouble();
                            double hingeRight = buf.readDouble();

                            if (key == null) {
                                // skip invalid
                                continue;
                            }

                            LockRenderProfile p = new LockRenderProfile(
                                    type,
                                    key,
                                    ox, oy, oz,
                                    rx, ry, rz,
                                    scale,
                                    hingeLeft, hingeRight
                            );

                            if (p.isValid()) {
                                out.put(key, p);
                            }
                        }

                        return new SyncLockRenderProfilesPayload(out);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncLockRenderProfilesPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> {
                try {
                    if (msg == null || msg.profiles == null) {
                        ClientLockRenderProfiles.setAll(Map.of());
                        return;
                    }
                    ClientLockRenderProfiles.setAll(msg.profiles);
                    LOG.info("[Locksmith][Client] SyncLockRenderProfilesPayload applied. count={}", msg.profiles.size());
                } catch (Throwable t) {
                    LOG.error("[Locksmith][Client] SyncLockRenderProfilesPayload apply failed (non-fatal).", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[Locksmith][SyncLockRenderProfilesPayload] handle enqueueWork failed (non-fatal).", t);
        }
    }
}
