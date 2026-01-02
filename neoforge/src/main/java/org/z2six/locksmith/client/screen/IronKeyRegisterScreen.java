// MainFile: neoforge/src/main/java/org/z2six/locksmith/client/screen/IronKeyRegisterScreen.java
package org.z2six.locksmith.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.network.RegisterIronKeyPayload;

/**
 * Simple registration GUI:
 * "Secret passphrase:" + textbox (max 64) + Confirm button.
 *
 * Fixes:
 * - Proper initial focus on the EditBox (no double-click required)
 * - Draws text after widgets at high Z (so blur never hides it)
 */
public class IronKeyRegisterScreen extends Screen {

    private static final Logger LOG = Constants.LOG;

    private static final int BOX_WIDTH = 220;
    private static final int BOX_HEIGHT = 20;

    private EditBox passphraseBox;
    private Button confirmButton;

    public IronKeyRegisterScreen() {
        super(Component.literal("Register Iron Key"));
    }

    @Override
    protected void init() {
        try {
            int cx = this.width / 2;
            int cy = this.height / 2;

            int boxY = cy - 10;
            int btnY = cy + 20;

            this.passphraseBox = new EditBox(
                    this.font,
                    cx - (BOX_WIDTH / 2),
                    boxY,
                    BOX_WIDTH,
                    BOX_HEIGHT,
                    Component.literal("Secret passphrase")
            );
            this.passphraseBox.setMaxLength(IronKeyItem.MAX_PASSPHRASE_LEN);
            this.passphraseBox.setValue("");
            this.passphraseBox.setCanLoseFocus(false);
            this.addRenderableWidget(this.passphraseBox);

            this.confirmButton = Button.builder(Component.literal("Confirm"), btn -> onConfirmPressed())
                    .bounds(cx - 50, btnY, 100, 20)
                    .build();
            this.addRenderableWidget(this.confirmButton);

            // ---- Focus fixes ----
            this.setInitialFocus(this.passphraseBox);
            this.setFocused(this.passphraseBox);
            this.passphraseBox.setFocused(true);

            updateConfirmEnabled();

            LOG.debug("[Locksmith][IronKeyRegisterScreen] init done. w={}, h={}", this.width, this.height);
        } catch (Throwable t) {
            LOG.error("[Locksmith][IronKeyRegisterScreen] init failed (non-fatal).", t);
        }
    }

    private void updateConfirmEnabled() {
        try {
            if (this.confirmButton == null || this.passphraseBox == null) return;
            String text = this.passphraseBox.getValue();
            boolean ok = text != null && !text.isBlank() && text.length() <= IronKeyItem.MAX_PASSPHRASE_LEN;
            this.confirmButton.active = ok;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][IronKeyRegisterScreen] updateConfirmEnabled failed (non-fatal).", t);
        }
    }

    private void onConfirmPressed() {
        try {
            if (this.passphraseBox == null) {
                LOG.warn("[Locksmith][IronKeyRegisterScreen] Confirm pressed but passphraseBox is null.");
                return;
            }

            String pass = this.passphraseBox.getValue();
            if (pass == null) pass = "";
            pass = pass.trim();

            if (pass.isEmpty()) {
                LOG.debug("[Locksmith][IronKeyRegisterScreen] Confirm pressed with empty passphrase; ignoring.");
                return;
            }

            if (pass.length() > IronKeyItem.MAX_PASSPHRASE_LEN) {
                LOG.warn("[Locksmith][IronKeyRegisterScreen] Passphrase too long (len={}); truncating to {}.",
                        pass.length(), IronKeyItem.MAX_PASSPHRASE_LEN);
                pass = pass.substring(0, IronKeyItem.MAX_PASSPHRASE_LEN);
            }

            LOG.debug("[Locksmith][IronKeyRegisterScreen] Sending RegisterIronKeyPayload to server. len={}", pass.length());
            PacketDistributor.sendToServer(new RegisterIronKeyPayload(pass));

            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.setScreen(null);
            }
        } catch (Throwable t) {
            LOG.error("[Locksmith][IronKeyRegisterScreen] onConfirmPressed failed (non-fatal).", t);
        }
    }

    @Override
    public void tick() {
        super.tick();
        try {
            updateConfirmEnabled();
        } catch (Throwable t) {
            LOG.warn("[Locksmith][IronKeyRegisterScreen] tick failed (non-fatal).", t);
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        boolean res = super.charTyped(codePoint, modifiers);
        updateConfirmEnabled();
        return res;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        try {
            if (keyCode == 257 /* GLFW_ENTER */ || keyCode == 335 /* GLFW_KP_ENTER */) {
                if (this.confirmButton != null && this.confirmButton.active) {
                    onConfirmPressed();
                    return true;
                }
            }

            if (keyCode == 256 /* GLFW_ESCAPE */) {
                onClose();
                return true;
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][IronKeyRegisterScreen] keyPressed hook failed (non-fatal).", t);
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx, mouseX, mouseY, partialTick);

        super.render(gfx, mouseX, mouseY, partialTick);

        try {
            int cx = this.width / 2;
            int cy = this.height / 2;

            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 1000);

            gfx.drawCenteredString(this.font, this.title, cx, cy - 55, 0xFFFFFF);
            gfx.drawCenteredString(this.font, "Secret passphrase:", cx, cy - 30, 0xFFFFFF);

            gfx.pose().popPose();
        } catch (Throwable t) {
            LOG.warn("[Locksmith][IronKeyRegisterScreen] render text overlay failed (non-fatal).", t);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
