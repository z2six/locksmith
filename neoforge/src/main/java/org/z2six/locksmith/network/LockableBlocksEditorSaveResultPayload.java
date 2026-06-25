// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/LockableBlocksEditorSaveResultPayload.java
package org.z2six.locksmith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.profile.LockableBlockValidationResult;

import java.lang.reflect.Method;
import java.util.List;

public record LockableBlocksEditorSaveResultPayload(
        boolean success,
        String message,
        List<LockableBlockValidationResult> validationResults
) implements CustomPacketPayload {
    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "lockable_blocks_editor_save_result");
    public static final Type<LockableBlocksEditorSaveResultPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, LockableBlocksEditorSaveResultPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeBoolean(msg != null && msg.success);
                        buf.writeUtf(msg == null || msg.message == null ? "" : msg.message, 512);
                        OpenLockableBlocksEditorPayload.writeValidationResults(buf, msg == null ? List.of() : msg.validationResults);
                    },
                    buf -> new LockableBlocksEditorSaveResultPayload(
                            buf.readBoolean(),
                            buf.readUtf(512),
                            OpenLockableBlocksEditorPayload.readValidationResults(buf)
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LockableBlocksEditorSaveResultPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> {
                try {
                    Class<?> screenClass = Class.forName("org.z2six.locksmith.client.screen.LockableBlocksEditorScreen");
                    Method method = screenClass.getMethod("applySaveResult", boolean.class, String.class, List.class);
                    method.invoke(null,
                            msg != null && msg.success,
                            msg == null ? "" : msg.message,
                            msg == null ? List.of() : msg.validationResults);
                } catch (Throwable t) {
                    LOG.error("[Locksmith][LockableBlocksEditorSaveResultPayload] Failed to apply result (non-fatal).", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[Locksmith][LockableBlocksEditorSaveResultPayload] handle failed (non-fatal).", t);
        }
    }
}
