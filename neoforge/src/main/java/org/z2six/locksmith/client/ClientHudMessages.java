// MainFile: neoforge/src/main/java/org/z2six/locksmith/client/ClientHudMessages.java
package org.z2six.locksmith.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.config.LocksmithClientConfig;

/**
 * Very small HUD overlay for Locksmith feedback:
 * - "Door locked" / "Chest locked"
 * - "Door locked: no key" / "Chest locked: no key"
 *
 * Driven entirely client-side, controlled by LocksmithClientConfig.
 */
public final class ClientHudMessages {

    private static final Logger LOG = Constants.LOG;

    private static Component currentMessage = null;
    private static int remainingTicks = 0;
    private static int currentColor = 0xFFFFFFFF;

    private ClientHudMessages() {
    }

    // ------------------------------------------------------------------------
    // Public helpers for doors/chests
    // ------------------------------------------------------------------------

    public static void showDoorLockedNoKey() {
        tryShow(Component.translatable("message.locksmith.door_locked_no_key"), 0xFFFF5555);
    }

    public static void showChestLockedNoKey() {
        tryShow(Component.translatable("message.locksmith.chest_locked_no_key"), 0xFFFF5555);
    }

    public static void showDoorLockSuccess() {
        tryShow(Component.translatable("message.locksmith.door_locked_success"), 0xFF55FF55);
    }

    public static void showChestLockSuccess() {
        tryShow(Component.translatable("message.locksmith.chest_locked_success"), 0xFF55FF55);
    }

    // ------------------------------------------------------------------------
    // Internal state helpers
    // ------------------------------------------------------------------------

    private static void tryShow(Component msg, int argb) {
        try {
            if (!LocksmithClientConfig.isHudMessagesEnabled()) {
                return;
            }
            if (msg == null) return;

            int ticks = LocksmithClientConfig.getHudMessageTicks();
            if (ticks <= 0) {
                ticks = 1;
            }

            currentMessage = msg;
            currentColor = argb;
            remainingTicks = ticks;

            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "[Locksmith][ClientHudMessages] Showing HUD message '{}' for {} client tick(s).",
                        msg.getString(), ticks
                );
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientHudMessages] tryShow failed (non-fatal).", t);
        }
    }

    // ------------------------------------------------------------------------
    // Tick hook: lifetime management (client ticks, 20 per second)
    // ------------------------------------------------------------------------

    public static void onClientTick(ClientTickEvent.Post event) {
        try {
            if (remainingTicks <= 0) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return;
            }

            // Optional: don't tick while paused
            if (mc.isPaused()) {
                return;
            }

            remainingTicks--;
            if (remainingTicks <= 0) {
                if (LOG.isDebugEnabled() && currentMessage != null) {
                    LOG.debug("[Locksmith][ClientHudMessages] HUD message '{}' expired.", currentMessage.getString());
                }
                currentMessage = null;
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientHudMessages] onClientTick failed (non-fatal).", t);
        }
    }

    // ------------------------------------------------------------------------
    // Render hook (registered from ClientInit)
    // ------------------------------------------------------------------------

    public static void onRenderGui(RenderGuiEvent.Post event) {
        try {
            if (currentMessage == null || remainingTicks <= 0) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) {
                return;
            }

            GuiGraphics gfx = event.getGuiGraphics();
            if (gfx == null) return;

            var window = mc.getWindow();
            if (window == null) {
                return;
            }

            int screenWidth = window.getGuiScaledWidth();
            int screenHeight = window.getGuiScaledHeight();
            Font font = mc.font;

            float scale = LocksmithClientConfig.getHudScale();
            if (scale <= 0.0F) {
                scale = 1.0F;
            }
            int offsetY = LocksmithClientConfig.getHudOffsetY();

            int centerX = screenWidth / 2;
            int baseY = screenHeight / 2 + offsetY;

            int textWidth = font.width(currentMessage);

            gfx.pose().pushPose();
            // Draw in front of almost everything else
            gfx.pose().translate(0.0F, 0.0F, 500.0F);
            gfx.pose().scale(scale, scale, 1.0F);

            float scaledCenterX = centerX / scale;
            float scaledY = baseY / scale;
            float drawX = scaledCenterX - (textWidth / 2.0F);

            gfx.drawString(font, currentMessage, (int) drawX, (int) scaledY, currentColor, false);
            gfx.pose().popPose();
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ClientHudMessages] onRenderGui failed (non-fatal).", t);
        }
    }
}
