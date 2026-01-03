// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/HudMessagePayload.java
package org.z2six.locksmith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

public record HudMessagePayload(byte typeId) implements CustomPacketPayload {

    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "hud_message");
    public static final Type<HudMessagePayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, HudMessagePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> buf.writeByte(msg.typeId),
                    buf -> new HudMessagePayload(buf.readByte())
            );

    // Message types (compact byte over network)
    public static final byte DOOR_LOCKED_NO_KEY = 1;
    public static final byte CHEST_LOCKED_NO_KEY = 2;
    public static final byte DOOR_LOCK_SUCCESS = 3;
    public static final byte CHEST_LOCK_SUCCESS = 4;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HudMessagePayload msg, IPayloadContext ctx) {
        try {
            if (msg == null || ctx == null) return;

            // This payload is S2C only. Enqueue on client thread.
            ctx.enqueueWork(() -> handleClient(msg));
        } catch (Throwable t) {
            LOG.error("[Locksmith][HudMessagePayload] enqueueWork failed (non-fatal).", t);
        }
    }

    private static void handleClient(HudMessagePayload msg) {
        try {
            if (msg == null) return;

            final byte t = msg.typeId();

            // IMPORTANT: This class must not hard-crash on dedicated server classloading.
            // It is only ever invoked on the CLIENT due to playToClient registration,
            // but we still keep this guarded and reflection-free.
            switch (t) {
                case DOOR_LOCKED_NO_KEY -> org.z2six.locksmith.client.ClientHudMessages.showDoorLockedNoKey();
                case CHEST_LOCKED_NO_KEY -> org.z2six.locksmith.client.ClientHudMessages.showChestLockedNoKey();
                case DOOR_LOCK_SUCCESS -> org.z2six.locksmith.client.ClientHudMessages.showDoorLockSuccess();
                case CHEST_LOCK_SUCCESS -> org.z2six.locksmith.client.ClientHudMessages.showChestLockSuccess();
                default -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[Locksmith][HudMessagePayload] Unknown typeId={} (ignored).", t);
                    }
                }
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("[Locksmith][HudMessagePayload] Handled HUD message typeId={}.", t);
            }
        } catch (Throwable t) {
            LOG.error("[Locksmith][HudMessagePayload] Client handler failed (non-fatal).", t);
        }
    }
}
