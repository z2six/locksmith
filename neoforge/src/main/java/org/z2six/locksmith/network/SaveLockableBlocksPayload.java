// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/SaveLockableBlocksPayload.java
package org.z2six.locksmith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.profile.LockableBlockEntry;
import org.z2six.locksmith.render.profile.LockableBlockProfileService;

import java.util.List;

public record SaveLockableBlocksPayload(List<LockableBlockEntry> entries) implements CustomPacketPayload {
    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "save_lockable_blocks");
    public static final Type<SaveLockableBlocksPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, SaveLockableBlocksPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> OpenLockableBlocksEditorPayload.writeEntries(buf, msg == null ? List.of() : msg.entries),
                    buf -> new SaveLockableBlocksPayload(OpenLockableBlocksEditorPayload.readEntries(buf))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveLockableBlocksPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> {
                try {
                    if (!(ctx.player() instanceof ServerPlayer sp)) return;
                    LockableBlockProfileService.SaveResult result = LockableBlockProfileService.saveFromEditor(
                            sp.getServer(),
                            sp,
                            msg == null ? List.of() : msg.entries
                    );
                    PacketDistributor.sendToPlayer(sp, new LockableBlocksEditorSaveResultPayload(
                            result.success(),
                            result.message(),
                            result.validationResults()
                    ));
                } catch (Throwable t) {
                    LOG.error("[Locksmith][SaveLockableBlocksPayload] Server save failed (non-fatal).", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[Locksmith][SaveLockableBlocksPayload] handle failed (non-fatal).", t);
        }
    }
}
