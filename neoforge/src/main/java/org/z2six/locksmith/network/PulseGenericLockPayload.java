package org.z2six.locksmith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

import java.lang.reflect.Method;

public record PulseGenericLockPayload(long posLong) implements CustomPacketPayload {
    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "pulse_generic_lock");
    public static final Type<PulseGenericLockPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, PulseGenericLockPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> buf.writeLong(msg.posLong),
                    buf -> new PulseGenericLockPayload(buf.readLong())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PulseGenericLockPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> {
                if (msg == null) return;
                long nowTick = currentClientGameTime();
                org.z2six.locksmith.render.ClientGenericLockPulse.trigger(msg.posLong, nowTick);
            });
        } catch (Throwable t) {
            LOG.error("[Locksmith][PulseGenericLockPayload] handle failed (non-fatal).", t);
        }
    }

    private static long currentClientGameTime() {
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcClass.getMethod("getInstance").invoke(null);
            Object level = mcClass.getField("level").get(mc);
            if (level == null) return 0L;
            Method getGameTime = level.getClass().getMethod("getGameTime");
            Object result = getGameTime.invoke(level);
            return result instanceof Long l ? l : 0L;
        } catch (Throwable t) {
            return 0L;
        }
    }
}
