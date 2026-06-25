// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/ReloadLockableBlocksEditorPayload.java
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
import org.z2six.locksmith.render.profile.LockableBlockProfileService;

public record ReloadLockableBlocksEditorPayload() implements CustomPacketPayload {
    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "reload_lockable_blocks_editor");
    public static final Type<ReloadLockableBlocksEditorPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ReloadLockableBlocksEditorPayload> STREAM_CODEC =
            StreamCodec.unit(new ReloadLockableBlocksEditorPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ReloadLockableBlocksEditorPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> {
                try {
                    if (!(ctx.player() instanceof ServerPlayer sp)) return;
                    if (!LockableBlockProfileService.canEdit(sp)) {
                        PacketDistributor.sendToPlayer(sp, new LockableBlocksEditorSaveResultPayload(
                                false,
                                "You do not have permission to reload Locksmith lockable blocks.",
                                LockableBlockProfileService.getValidationSnapshot()
                        ));
                        return;
                    }

                    LockableBlockProfileService.reloadFromDisk();
                    LockableBlockProfileService.syncProfilesToAll(sp.getServer());
                    PacketDistributor.sendToPlayer(sp, new OpenLockableBlocksEditorPayload(
                            LockableBlockProfileService.getEntriesSnapshot(),
                            LockableBlockProfileService.getValidationSnapshot()
                    ));
                } catch (Throwable t) {
                    LOG.error("[Locksmith][ReloadLockableBlocksEditorPayload] Server reload failed (non-fatal).", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[Locksmith][ReloadLockableBlocksEditorPayload] handle failed (non-fatal).", t);
        }
    }
}
