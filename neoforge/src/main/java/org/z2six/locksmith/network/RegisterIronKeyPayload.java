// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/RegisterIronKeyPayload.java
package org.z2six.locksmith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.registry.ModItems;
import org.z2six.locksmith.util.ItemStackDataUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * C2S: register the iron key in the player's MAIN hand.
 * Server computes SHA-256 FIRST, then stores:
 *  - LocksmithKeyHash
 *  - LocksmithRegisteredBy
 * in CustomData.
 */
public record RegisterIronKeyPayload(String passphrase) implements CustomPacketPayload {

    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "register_iron_key");
    public static final Type<RegisterIronKeyPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, RegisterIronKeyPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        String s = (msg == null || msg.passphrase == null) ? "" : msg.passphrase;
                        buf.writeUtf(s, IronKeyItem.MAX_PASSPHRASE_LEN);
                    },
                    buf -> new RegisterIronKeyPayload(buf.readUtf(IronKeyItem.MAX_PASSPHRASE_LEN))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RegisterIronKeyPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> handleServer(msg, ctx));
        } catch (Throwable t) {
            LOG.error("[Locksmith][RegisterIronKeyPayload] handle enqueueWork failed (non-fatal).", t);
        }
    }

    private static void handleServer(RegisterIronKeyPayload msg, IPayloadContext ctx) {
        Player player = null;
        try {
            player = ctx.player();
            if (player == null) {
                LOG.warn("[Locksmith][RegisterIronKeyPayload] Server handler: ctx.player() was null.");
                return;
            }

            String pass = (msg == null || msg.passphrase == null) ? "" : msg.passphrase;
            pass = pass.trim();

            if (pass.isEmpty()) {
                LOG.debug("[Locksmith][RegisterIronKeyPayload] Empty passphrase from player {}; ignoring.",
                        player.getName().getString());
                return;
            }

            if (pass.length() > IronKeyItem.MAX_PASSPHRASE_LEN) {
                LOG.warn("[Locksmith][RegisterIronKeyPayload] Passphrase too long from player {} (len={}); truncating.",
                        player.getName().getString(), pass.length());
                pass = pass.substring(0, IronKeyItem.MAX_PASSPHRASE_LEN);
            }

            ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (stack == null || stack.isEmpty()) {
                LOG.debug("[Locksmith][RegisterIronKeyPayload] Player {} main-hand empty; ignoring.",
                        player.getName().getString());
                return;
            }

            if (!stack.is(ModItems.KEY_IRON.get())) {
                LOG.warn("[Locksmith][RegisterIronKeyPayload] Player {} tried to register but main-hand is not iron_key: {}",
                        player.getName().getString(), stack.getItem());
                return;
            }

            if (IronKeyItem.isRegistered(stack)) {
                LOG.debug("[Locksmith][RegisterIronKeyPayload] Player {} iron_key already registered; ignoring.",
                        player.getName().getString());
                return;
            }

            // Compute SHA-256 FIRST
            String hashHex = sha256Hex(pass);
            if (hashHex == null || hashHex.isBlank()) {
                LOG.warn("[Locksmith][RegisterIronKeyPayload] sha256Hex returned blank for player {} (non-fatal).",
                        player.getName().getString());
                return;
            }

            boolean wroteHash = ItemStackDataUtil.putString(stack, IronKeyItem.DATA_KEY_HASH, hashHex);
            boolean wroteBy = ItemStackDataUtil.putString(stack, IronKeyItem.DATA_REGISTERED_BY, player.getName().getString());

            if (!wroteHash || !wroteBy) {
                LOG.error("[Locksmith][RegisterIronKeyPayload] Failed writing CustomData for player {} (non-fatal). wroteHash={}, wroteBy={}",
                        player.getName().getString(), wroteHash, wroteBy);
                return;
            }

            try {
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                player.getInventory().setChanged();
            } catch (Throwable t) {
                LOG.warn("[Locksmith][RegisterIronKeyPayload] Failed to force-sync item stack for player {} (non-fatal).",
                        player.getName().getString(), t);
            }

            LOG.info("[Locksmith] Registered iron_key for player {} (stored SHA-256 hex, len=64).",
                    player.getName().getString());

        } catch (Throwable t) {
            LOG.error("[Locksmith][RegisterIronKeyPayload] Server handler failed (non-fatal). player={}",
                    (player == null ? "null" : player.getName().getString()), t);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (Throwable t) {
            LOG.error("[Locksmith][RegisterIronKeyPayload] sha256Hex failed (non-fatal).", t);
            return "";
        }
    }

    private static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xFF;
            sb.append(Character.forDigit((v >>> 4) & 0xF, 16));
            sb.append(Character.forDigit(v & 0xF, 16));
        }
        return sb.toString();
    }
}
