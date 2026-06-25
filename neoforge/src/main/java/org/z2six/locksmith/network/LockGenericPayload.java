// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/LockGenericPayload.java
package org.z2six.locksmith.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.registry.ModItems;
import org.z2six.locksmith.render.profile.LockableBlockProfileService;
import org.z2six.locksmith.world.GenericLockSavedData;

public record LockGenericPayload(long posLong) implements CustomPacketPayload {
    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "lock_generic");
    public static final Type<LockGenericPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, LockGenericPayload> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> buf.writeLong(msg.posLong), buf -> new LockGenericPayload(buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LockGenericPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> handleServer(msg, ctx));
        } catch (Throwable t) {
            LOG.error("[Locksmith][LockGenericPayload] enqueueWork failed (non-fatal).", t);
        }
    }

    private static void handleServer(LockGenericPayload msg, IPayloadContext ctx) {
        try {
            if (msg == null || !(ctx.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            BlockPos pos = BlockPos.of(msg.posLong);
            BlockState state = level.getBlockState(pos);
            if (state == null || state.isAir()) return;

            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (!LockableBlockProfileService.isLockableGeneric(blockId)) return;

            ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (held == null || held.isEmpty() || !(held.getItem() instanceof IronKeyItem)) return;
            if (!held.is(ModItems.KEY_IRON.get()) || !IronKeyItem.isRegistered(held)) return;

            GenericLockSavedData data = GenericLockSavedData.get(level);
            if (data.isLockedLong(msg.posLong)) return;

            String hash = IronKeyItem.getHashOrEmpty(held);
            boolean added = data.putLockLong(msg.posLong, hash);
            if (!added) return;

            for (ServerPlayer other : level.players()) {
                PacketDistributor.sendToPlayer(other, new AddGenericLockPayload(msg.posLong, hash));
            }
            PacketDistributor.sendToPlayer(player, new HudMessagePayload(HudMessagePayload.GENERIC_LOCK_SUCCESS));
        } catch (Throwable t) {
            LOG.error("[Locksmith][LockGenericPayload] Server handler failed (non-fatal).", t);
        }
    }
}
