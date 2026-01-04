// MainFile: neoforge/src/main/java/org/z2six/locksmith/client/ClientCuriosKeyEquipEvents.java
package org.z2six.locksmith.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.network.QuickEquipCuriosKeyPayload;
import org.z2six.locksmith.registry.ModItems;

/**
 * Client-only screen listener:
 * - When any container screen is open (including Curios screen),
 * - and user Shift + Right Clicks a REGISTERED Locksmith iron key stack,
 * - send C2S to move it into Curios "key" slot (optional integration).
 *
 * No direct dependency on Curios screen classes; we only rely on vanilla container screen APIs.
 */
public final class ClientCuriosKeyEquipEvents {

    private static final Logger LOG = Constants.LOG;

    private ClientCuriosKeyEquipEvents() {
    }

    public static void register() {
        try {
            NeoForge.EVENT_BUS.addListener(ClientCuriosKeyEquipEvents::onMousePressedPre);
            LOG.info("[Locksmith] ClientCuriosKeyEquipEvents registered.");
        } catch (Throwable t) {
            LOG.warn("[Locksmith] Failed to register ClientCuriosKeyEquipEvents (non-fatal).", t);
        }
    }

    public static void onMousePressedPre(ScreenEvent.MouseButtonPressed.Pre event) {
        try {
            if (event == null) return;

            // Only if Curios exists; otherwise do nothing
            if (!ModList.get().isLoaded("curios")) return;

            // Right mouse button is usually 1
            if (event.getButton() != 1) return;

            // Require Shift
            if (!net.minecraft.client.gui.screens.Screen.hasShiftDown()) return;

            if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

            Slot hovered = null;
            try {
                hovered = screen.getSlotUnderMouse();
            } catch (Throwable t) {
                hovered = null;
            }
            if (hovered == null) return;

            ItemStack stack = hovered.getItem();
            if (stack == null || stack.isEmpty()) return;

            if (!stack.is(ModItems.KEY_IRON.get())) return;

            if (!IronKeyItem.isRegistered(stack)) {
                // Unregistered keys are not quick-equipped; Curios enforcement will also eject if inserted manually.
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith] Shift+RMB on unregistered iron_key ignored (quick-equip requires registered).");
                }
                return;
            }

            int containerId;
            int slotIndex;
            try {
                containerId = screen.getMenu().containerId;
                slotIndex = hovered.index;
            } catch (Throwable t) {
                LOG.debug("[Locksmith] Failed reading container id/slot index for quick-equip (non-fatal).", t);
                return;
            }

            // Cancel default click behavior and send our packet
            event.setCanceled(true);

            try {
                PacketDistributor.sendToServer(new QuickEquipCuriosKeyPayload(containerId, slotIndex));
            } catch (Throwable t) {
                LOG.warn("[Locksmith] Failed sending QuickEquipCuriosKeyPayload (non-fatal).", t);
                return;
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("[Locksmith] Sent quick-equip request: containerId={} slotIndex={}", containerId, slotIndex);
            }

        } catch (Throwable t) {
            LOG.debug("[Locksmith][ClientCuriosKeyEquipEvents] onMousePressedPre failed (non-fatal).", t);
        }
    }
}
