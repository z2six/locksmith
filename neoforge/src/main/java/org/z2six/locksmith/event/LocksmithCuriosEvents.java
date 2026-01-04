// MainFile: neoforge/src/main/java/org/z2six/locksmith/event/LocksmithCuriosEvents.java
package org.z2six.locksmith.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.registry.ModItems;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Optional Curios enforcement:
 * - If Curios is present and player has a Curios "key" slot,
 *   any UNREGISTERED Locksmith iron_key found in that slot is ejected back to inventory.
 *
 * This prevents bypass via manual dragging of an unregistered key into the Curios slot.
 *
 * Curios interaction uses reflection only; no hard dependency.
 */
public final class LocksmithCuriosEvents {

    private static final Logger LOG = Constants.LOG;

    private LocksmithCuriosEvents() {
    }

    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        try {
            Player player = event.getEntity();
            if (player == null) return;

            if (player.level() == null || player.level().isClientSide()) return;

            if (!ModList.get().isLoaded("curios")) return;

            // Reduce overhead: check once per second (20 ticks) per player
            if ((player.tickCount % 20) != 0) return;

            enforceRegisteredKeyInCuriosSlot(player);

        } catch (Throwable t) {
            LOG.debug("[Locksmith][LocksmithCuriosEvents] onPlayerTickPost failed (non-fatal).", t);
        }
    }

    private static void enforceRegisteredKeyInCuriosSlot(Player player) {
        try {
            if (player == null) return;

            Optional<Object> dynamicOpt = getCuriosDynamicHandler(player, "key");
            if (dynamicOpt.isEmpty()) return;

            Object dynamic = dynamicOpt.get();

            Method getSlots = dynamic.getClass().getMethod("getSlots");
            int slots = (int) getSlots.invoke(dynamic);
            if (slots <= 0) return;

            Method getStackInSlot = dynamic.getClass().getMethod("getStackInSlot", int.class);
            Method setStackInSlot = dynamic.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);

            for (int i = 0; i < slots; i++) {
                Object stackObj = getStackInSlot.invoke(dynamic, i);
                if (!(stackObj instanceof ItemStack stack)) continue;
                if (stack.isEmpty()) continue;

                if (!stack.is(ModItems.KEY_IRON.get())) continue;

                boolean registered = IronKeyItem.isRegistered(stack);
                if (registered) continue;

                // Eject unregistered key
                ItemStack toGive = stack.copy();
                toGive.setCount(1);

                boolean added = player.getInventory().add(toGive);
                if (!added) {
                    // Try dropping as fallback
                    try {
                        player.drop(toGive, false);
                        LOG.debug("[Locksmith][Curios] Ejected unregistered key by dropping near {} (inventory full).",
                                player.getName().getString());
                    } catch (Throwable t) {
                        LOG.warn("[Locksmith][Curios] Failed to drop ejected unregistered key for {} (non-fatal).",
                                player.getName().getString(), t);
                        // If we can't give or drop reliably, do not delete it.
                        continue;
                    }
                }

                // Clear Curios slot
                setStackInSlot.invoke(dynamic, i, ItemStack.EMPTY);

                try {
                    player.getInventory().setChanged();
                } catch (Throwable ignored) {
                }

                LOG.info("[Locksmith] Removed UNREGISTERED iron_key from Curios 'key' slot for {}.",
                        player.getName().getString());
            }

        } catch (Throwable t) {
            LOG.debug("[Locksmith][Curios] enforceRegisteredKeyInCuriosSlot failed (non-fatal).", t);
        }
    }

    /**
     * Returns Curios IDynamicStackHandler object for a slot type via reflection, if present.
     */
    private static Optional<Object> getCuriosDynamicHandler(Player player, String slotType) {
        try {
            if (player == null) return Optional.empty();
            if (slotType == null || slotType.isBlank()) return Optional.empty();
            if (!ModList.get().isLoaded("curios")) return Optional.empty();

            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getInv = curiosApi.getMethod("getCuriosInventory", Class.forName("net.minecraft.world.entity.LivingEntity"));
            Object invOptObj = getInv.invoke(null, player);

            if (!(invOptObj instanceof Optional<?> invOpt) || invOpt.isEmpty()) return Optional.empty();
            Object curiosHandler = invOpt.get();
            if (curiosHandler == null) return Optional.empty();

            Method getStacksHandler = curiosHandler.getClass().getMethod("getStacksHandler", String.class);
            Object stacksOptObj = getStacksHandler.invoke(curiosHandler, slotType);

            if (!(stacksOptObj instanceof Optional<?> stacksOpt) || stacksOpt.isEmpty()) return Optional.empty();
            Object stacksHandler = stacksOpt.get();
            if (stacksHandler == null) return Optional.empty();

            Method getStacks = stacksHandler.getClass().getMethod("getStacks");
            Object dynamic = getStacks.invoke(stacksHandler);

            return Optional.ofNullable(dynamic);
        } catch (Throwable t) {
            LOG.debug("[Locksmith][Curios] getCuriosDynamicHandler failed (non-fatal).", t);
            return Optional.empty();
        }
    }
}
