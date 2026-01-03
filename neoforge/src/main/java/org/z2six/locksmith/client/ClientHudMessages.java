// MainFile: neoforge/src/main/java/org/z2six/locksmith/client/ClientHudMessages.java
package org.z2six.locksmith.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.config.LocksmithClientConfig;

/**
 * Locksmith HUD feedback.
 *
 * IMPORTANT:
 * - We intentionally use ONLY the vanilla overlay message (above-hotbar) because it:
 *   - persists longer
 *   - fades smoothly
 *   - is consistent with Minecraft UX
 *
 * - We DO NOT render our own centered HUD message anymore, because it caused duplicate messages.
 *
 * - We honor the client config toggle hudMessagesEnabled.
 *
 * Partial coloring:
 * - Achieved by using translatable strings with %s placeholders and passing styled Components as arguments.
 */
public final class ClientHudMessages {

    private static final Logger LOG = Constants.LOG;

    private ClientHudMessages() {
    }

    // ------------------------------------------------------------------------
    // Public helpers
    // ------------------------------------------------------------------------

    public static void showDoorLockedNoKey() {
        showLockedNoKey(makeTargetDoor(), makeLockedWordDenied());
    }

    public static void showChestLockedNoKey() {
        showLockedNoKey(makeTargetChest(), makeLockedWordDenied());
    }

    public static void showDoorLockSuccess() {
        showLockSuccess(makeTargetDoor(), makeLockedWordSuccess());
    }

    public static void showChestLockSuccess() {
        showLockSuccess(makeTargetChest(), makeLockedWordSuccess());
    }

    // ------------------------------------------------------------------------
    // Message builders (partial coloring)
    // ------------------------------------------------------------------------

    private static Component makeTargetDoor() {
        // A single colored word inside the sentence.
        return Component.translatable("message.locksmith.target.door").withStyle(ChatFormatting.AQUA);
    }

    private static Component makeTargetChest() {
        return Component.translatable("message.locksmith.target.chest").withStyle(ChatFormatting.AQUA);
    }

    private static Component makeLockedWordDenied() {
        return Component.translatable("message.locksmith.word.locked").withStyle(ChatFormatting.RED);
    }

    private static Component makeLockedWordSuccess() {
        return Component.translatable("message.locksmith.word.locked").withStyle(ChatFormatting.GREEN);
    }

    // ------------------------------------------------------------------------
    // Emission (vanilla overlay only)
    // ------------------------------------------------------------------------

    private static void showLockedNoKey(Component target, Component lockedWord) {
        try {
            if (!isEnabled()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][ClientHudMessages] hudMessagesEnabled=false; skipping locked_no_key.");
                }
                return;
            }

            // Requires translation key: message.locksmith.locked_no_key_fmt
            Component msg = Component.translatable("message.locksmith.locked_no_key_fmt", target, lockedWord);
            pushVanillaOverlay(msg);

            if (LOG.isInfoEnabled()) {
                LOG.info("[Locksmith][ClientHudMessages] overlay=locked_no_key msg='{}'", safeString(msg));
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientHudMessages] showLockedNoKey failed (non-fatal).", t);
        }
    }

    private static void showLockSuccess(Component target, Component lockedWord) {
        try {
            if (!isEnabled()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][ClientHudMessages] hudMessagesEnabled=false; skipping locked_success.");
                }
                return;
            }

            // Requires translation key: message.locksmith.lock_success_fmt
            Component msg = Component.translatable("message.locksmith.lock_success_fmt", target, lockedWord);
            pushVanillaOverlay(msg);

            if (LOG.isInfoEnabled()) {
                LOG.info("[Locksmith][ClientHudMessages] overlay=lock_success msg='{}'", safeString(msg));
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientHudMessages] showLockSuccess failed (non-fatal).", t);
        }
    }

    private static boolean isEnabled() {
        try {
            return LocksmithClientConfig.isHudMessagesEnabled();
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientHudMessages] Failed reading hudMessagesEnabled; defaulting to enabled (non-fatal).", t);
            return true;
        }
    }

    private static void pushVanillaOverlay(Component msg) {
        try {
            if (msg == null) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            if (mc.gui == null) return;

            // Above-hotbar overlay. Not chat.
            mc.gui.setOverlayMessage(msg, false);
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientHudMessages] pushVanillaOverlay failed (non-fatal).", t);
        }
    }

    private static String safeString(Component c) {
        try {
            return c != null ? c.getString() : "null";
        } catch (Throwable t) {
            return "<?>"; // never crash logging
        }
    }
}
