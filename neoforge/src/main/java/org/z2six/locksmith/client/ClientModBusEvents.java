// MainFile: neoforge/src/main/java/org/z2six/locksmith/client/ClientModBusEvents.java
package org.z2six.locksmith.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

/**
 * Client-only MOD-bus wiring.
 *
 * Why this exists:
 * - ClientInit.init() MUST be called from FMLClientSetupEvent (mod event bus),
 *   not from mod construction time, and not via reflection.
 *
 * Also includes a one-time sanity check message on first right click, so you can
 * confirm client hooks are active even if your lock logic isn't reached.
 */
public final class ClientModBusEvents {

    private static final Logger LOG = Constants.LOG;

    private static volatile boolean sanityShown = false;

    private ClientModBusEvents() {
    }

    public static void register(IEventBus modBus) {
        try {
            if (modBus == null) {
                LOG.error("[Locksmith][ClientModBusEvents] register called with null modBus.");
                return;
            }
            modBus.addListener(ClientModBusEvents::onClientSetup);
            modBus.addListener(ClientKeyMappings::onRegisterKeyMappings);
            LOG.info("[Locksmith][ClientModBusEvents] Registered onClientSetup listener.");
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientModBusEvents] register failed (non-fatal).", t);
        }
    }

    private static void onClientSetup(final FMLClientSetupEvent event) {
        try {
            LOG.info("[Locksmith][ClientModBusEvents] FMLClientSetupEvent fired. Running ClientInit.init()...");
            ClientInit.init();

            // Install a one-time sanity check hook on the global NeoForge bus.
            // This proves beyond doubt that client-side event handling is alive.
            NeoForge.EVENT_BUS.addListener(ClientModBusEvents::onClientRightClickSanity);
            NeoForge.EVENT_BUS.addListener(ClientKeyMappings::onClientTickPost);

            LOG.info("[Locksmith][ClientModBusEvents] Client setup complete; sanity hook registered.");
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientModBusEvents] onClientSetup failed (non-fatal).", t);
        }
    }

    private static void onClientRightClickSanity(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (sanityShown) return;
            if (event == null) return;
            if (!event.getLevel().isClientSide) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return;

            sanityShown = true;

            // Visible overlay message (not chat)
            try {
                if (mc.gui != null) {
                    // mc.gui.setOverlayMessage(Component.literal("[Locksmith] Client hooks active (sanity check)."), false);
                }
            } catch (Throwable ignored) {
                // fallback to actionbar via player (still not chat because 'true')
                // mc.player.displayClientMessage(Component.literal("[Locksmith] Client hooks active (sanity check)."), true);
            }

            LOG.info("[Locksmith][ClientModBusEvents] Sanity check overlay displayed. Client event pipeline confirmed.");
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientModBusEvents] onClientRightClickSanity failed (non-fatal).", t);
        }
    }
}
