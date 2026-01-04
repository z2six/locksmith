// MainFile: neoforge/src/main/java/org/z2six/locksmith/network/QuickEquipCuriosKeyPayload.java
package org.z2six.locksmith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.registry.ModItems;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * C2S: Shift+RMB quick-equip a REGISTERED iron key into Curios "key" slot (optional).
 *
 * Client sends:
 * - containerId (menu container id)
 * - slotIndex (index into menu.slots)
 *
 * Server validates:
 * - player + container id match
 * - slot index bounds
 * - slot contains our iron key
 * - key is registered (non-blank LocksmithKeyHash)
 * - Curios is loaded
 *
 * Then:
 * - inserts into first empty Curios "key" slot
 * - removes stack from the source slot
 *
 * All Curios interaction is done via reflection so Locksmith has no hard dependency.
 */
public record QuickEquipCuriosKeyPayload(int containerId, int slotIndex) implements CustomPacketPayload {

    private static final Logger LOG = Constants.LOG;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "quick_equip_curios_key");
    public static final Type<QuickEquipCuriosKeyPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, QuickEquipCuriosKeyPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        int c = (msg == null) ? -1 : msg.containerId();
                        int s = (msg == null) ? -1 : msg.slotIndex();
                        buf.writeVarInt(c);
                        buf.writeVarInt(s);
                    },
                    buf -> new QuickEquipCuriosKeyPayload(buf.readVarInt(), buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(QuickEquipCuriosKeyPayload msg, IPayloadContext ctx) {
        try {
            ctx.enqueueWork(() -> handleServer(msg, ctx));
        } catch (Throwable t) {
            LOG.error("[Locksmith][QuickEquipCuriosKeyPayload] enqueueWork failed (non-fatal).", t);
        }
    }

    private static void handleServer(QuickEquipCuriosKeyPayload msg, IPayloadContext ctx) {
        Player player = null;
        try {
            player = ctx.player();
            if (player == null) {
                LOG.warn("[Locksmith][QuickEquipCuriosKeyPayload] ctx.player() was null.");
                return;
            }

            if (msg == null) {
                LOG.debug("[Locksmith][QuickEquipCuriosKeyPayload] msg was null; ignoring.");
                return;
            }

            if (!ModList.get().isLoaded("curios")) {
                LOG.debug("[Locksmith][QuickEquipCuriosKeyPayload] Curios not loaded; ignoring request from {}.",
                        player.getName().getString());
                return;
            }

            AbstractContainerMenu menu = player.containerMenu;
            if (menu == null) {
                LOG.warn("[Locksmith][QuickEquipCuriosKeyPayload] player.containerMenu was null for {}.",
                        player.getName().getString());
                return;
            }

            if (menu.containerId != msg.containerId()) {
                LOG.warn("[Locksmith][QuickEquipCuriosKeyPayload] containerId mismatch for {}. client={} server={}",
                        player.getName().getString(), msg.containerId(), menu.containerId);
                return;
            }

            int slotIndex = msg.slotIndex();
            if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
                LOG.warn("[Locksmith][QuickEquipCuriosKeyPayload] invalid slotIndex {} from {} (slots={}).",
                        slotIndex, player.getName().getString(), menu.slots.size());
                return;
            }

            Slot slot = menu.slots.get(slotIndex);
            if (slot == null) {
                LOG.warn("[Locksmith][QuickEquipCuriosKeyPayload] slot was null at index {} for {}.",
                        slotIndex, player.getName().getString());
                return;
            }

            ItemStack stack = slot.getItem();
            if (stack == null || stack.isEmpty()) {
                LOG.debug("[Locksmith][QuickEquipCuriosKeyPayload] slot {} empty for {}; ignoring.",
                        slotIndex, player.getName().getString());
                return;
            }

            if (!stack.is(ModItems.KEY_IRON.get())) {
                LOG.warn("[Locksmith][QuickEquipCuriosKeyPayload] {} attempted quick-equip but slot {} is not iron_key: {}",
                        player.getName().getString(), slotIndex, stack.getItem());
                return;
            }

            if (!IronKeyItem.isRegistered(stack)) {
                LOG.debug("[Locksmith][QuickEquipCuriosKeyPayload] {} attempted quick-equip with UNREGISTERED key; denied.",
                        player.getName().getString());
                return;
            }

            boolean inserted = tryInsertIntoCuriosKeySlot(player, stack);
            if (!inserted) {
                LOG.debug("[Locksmith][QuickEquipCuriosKeyPayload] No empty Curios 'key' slot (or API failure) for {}.",
                        player.getName().getString());
                return;
            }

            // Remove from source slot after successful insertion
            try {
                slot.set(ItemStack.EMPTY);
                slot.setChanged();
            } catch (Throwable t) {
                LOG.warn("[Locksmith][QuickEquipCuriosKeyPayload] Failed clearing source slot {} for {} (non-fatal).",
                        slotIndex, player.getName().getString(), t);
            }

            try {
                menu.broadcastChanges();
                player.getInventory().setChanged();
            } catch (Throwable t) {
                LOG.debug("[Locksmith][QuickEquipCuriosKeyPayload] broadcastChanges failed (non-fatal) for {}.",
                        player.getName().getString(), t);
            }

            LOG.info("[Locksmith] Quick-equipped registered iron_key into Curios 'key' slot for {}.",
                    player.getName().getString());

        } catch (Throwable t) {
            LOG.error("[Locksmith][QuickEquipCuriosKeyPayload] Server handler failed (non-fatal). player={}",
                    (player == null ? "null" : player.getName().getString()), t);
        }
    }

    /**
     * Reflection path (Curios 1.21.1+):
     * CuriosApi.getCuriosInventory(LivingEntity) -> Optional<ICuriosItemHandler>
     * handler.getStacksHandler("key") -> Optional<ICurioStacksHandler>
     * stacksHandler.getStacks() -> IDynamicStackHandler
     * dynamic.getSlots(), dynamic.getStackInSlot(i), dynamic.setStackInSlot(i, stackToInsert)
     */
    private static boolean tryInsertIntoCuriosKeySlot(Player player, ItemStack stack) {
        try {
            if (player == null || stack == null || stack.isEmpty()) return false;
            if (!ModList.get().isLoaded("curios")) return false;

            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getInv = curiosApi.getMethod("getCuriosInventory", Class.forName("net.minecraft.world.entity.LivingEntity"));
            Object invOptObj = getInv.invoke(null, player);

            if (!(invOptObj instanceof Optional<?> invOpt) || invOpt.isEmpty()) {
                LOG.debug("[Locksmith][Curios] getCuriosInventory returned empty for {}.", player.getName().getString());
                return false;
            }

            Object curiosHandler = invOpt.get();
            if (curiosHandler == null) return false;

            Method getStacksHandler = curiosHandler.getClass().getMethod("getStacksHandler", String.class);
            Object stacksOptObj = getStacksHandler.invoke(curiosHandler, "key");

            if (!(stacksOptObj instanceof Optional<?> stacksOpt) || stacksOpt.isEmpty()) {
                LOG.debug("[Locksmith][Curios] No stacks handler for slotType 'key' for {}.", player.getName().getString());
                return false;
            }

            Object stacksHandler = stacksOpt.get();
            if (stacksHandler == null) return false;

            Method getStacks = stacksHandler.getClass().getMethod("getStacks");
            Object dynamic = getStacks.invoke(stacksHandler);
            if (dynamic == null) {
                LOG.debug("[Locksmith][Curios] stacksHandler.getStacks returned null for {}.", player.getName().getString());
                return false;
            }

            Method getSlots = dynamic.getClass().getMethod("getSlots");
            int slots = (int) getSlots.invoke(dynamic);
            if (slots <= 0) {
                LOG.debug("[Locksmith][Curios] Dynamic handler has 0 slots for {}.", player.getName().getString());
                return false;
            }

            Method getStackInSlot = dynamic.getClass().getMethod("getStackInSlot", int.class);
            Method setStackInSlot = dynamic.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);

            ItemStack toInsert = stack.copy();
            toInsert.setCount(1);

            for (int i = 0; i < slots; i++) {
                Object existingObj = getStackInSlot.invoke(dynamic, i);
                if (!(existingObj instanceof ItemStack existing)) continue;

                if (existing.isEmpty()) {
                    setStackInSlot.invoke(dynamic, i, toInsert);
                    return true;
                }
            }

            return false;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][Curios] tryInsertIntoCuriosKeySlot failed (non-fatal).", t);
            return false;
        }
    }
}
