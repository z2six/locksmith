package org.z2six.locksmith.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

public final class ClientKeyMappings {
    private static final Logger LOG = Constants.LOG;
    private static final String CATEGORY = "key.categories.locksmith";

    private static final KeyMapping TOGGLE_SNEAK_LOCKING = new KeyMapping(
            "key.locksmith.toggle_sneak_locking",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_K,
            CATEGORY
    );

    private static boolean sneakLockingEnabled = false;

    private ClientKeyMappings() {
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        try {
            if (event == null) return;
            event.register(TOGGLE_SNEAK_LOCKING);
        } catch (Throwable t) {
            LOG.error("[Locksmith][ClientKeyMappings] Failed to register key mappings (non-fatal).", t);
        }
    }

    public static void onClientTickPost(ClientTickEvent.Post event) {
        try {
            while (TOGGLE_SNEAK_LOCKING.consumeClick()) {
                sneakLockingEnabled = !sneakLockingEnabled;
                ClientHudMessages.showSneakLockingToggle(sneakLockingEnabled);
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientKeyMappings] onClientTickPost failed (non-fatal).", t);
        }
    }

    public static boolean isSneakLockingEnabled() {
        return sneakLockingEnabled;
    }
}
