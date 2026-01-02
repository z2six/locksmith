// MainFile: neoforge/src/main/java/org/z2six/locksmith/item/IronKeyItem.java
package org.z2six.locksmith.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.util.ItemStackDataUtil;

import java.lang.reflect.Constructor;
import java.util.List;

/**
 * Iron Key:
 * - Tooltip shows either "Unregistered" or "Registered"
 * - Tooltip also shows "Tools & Utilities" (as requested)
 * - RMB while held in MAIN hand opens registration GUI (client-side only) if unregistered.
 *
 * Important: dedicated server safe (no direct client class references).
 */
public class IronKeyItem extends Item {

    private static final Logger LOG = Constants.LOG;

    public static final String DATA_KEY_HASH = "LocksmithKeyHash";
    public static final int MAX_PASSPHRASE_LEN = 64;

    private static final String CLIENT_SCREEN_CLASS = "org.z2six.locksmith.client.screen.IronKeyRegisterScreen";

    public IronKeyItem(Properties properties) {
        super(properties);
    }

    public static boolean isRegistered(ItemStack stack) {
        return ItemStackDataUtil.hasNonBlankString(stack, DATA_KEY_HASH);
    }

    public static String getHashOrEmpty(ItemStack stack) {
        return ItemStackDataUtil.getString(stack, DATA_KEY_HASH);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tooltip, flag);

        try {
            // Per your request: show creative tab name line.
            // Minecraft does not normally show this in tooltips, so we add it ourselves.
            tooltip.add(Component.translatable("tooltip.locksmith.tools_and_utilities")
                    .withStyle(ChatFormatting.DARK_GRAY));

            if (!isRegistered(stack)) {
                tooltip.add(Component.translatable("tooltip.locksmith.unregistered")
                        .withStyle(ChatFormatting.GRAY));
                LOG.debug("[Locksmith][IronKeyItem] Tooltip: Unregistered");
            } else {
                tooltip.add(Component.translatable("tooltip.locksmith.registered")
                        .withStyle(ChatFormatting.GREEN));
                LOG.debug("[Locksmith][IronKeyItem] Tooltip: Registered");
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][IronKeyItem] appendHoverText failed (non-fatal).", t);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        try {
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResultHolder.pass(stack);
            }

            if (isRegistered(stack)) {
                return InteractionResultHolder.pass(stack);
            }

            if (level.isClientSide) {
                boolean opened = openRegisterScreenClientSafe();
                if (!opened) {
                    LOG.warn("[Locksmith][IronKeyItem] Failed to open register screen (non-fatal).");
                }
                return InteractionResultHolder.success(stack);
            }

            return InteractionResultHolder.consume(stack);

        } catch (Throwable t) {
            LOG.error("[Locksmith][IronKeyItem] use failed (non-fatal).", t);
            return InteractionResultHolder.fail(stack);
        }
    }

    private static boolean openRegisterScreenClientSafe() {
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcClass.getMethod("getInstance").invoke(null);
            if (mc == null) {
                LOG.warn("[Locksmith][IronKeyItem] openRegisterScreenClientSafe: Minecraft.getInstance returned null.");
                return false;
            }

            Object currentScreen = mcClass.getField("screen").get(mc);
            if (currentScreen != null) {
                LOG.debug("[Locksmith][IronKeyItem] Screen already open ({}); not opening register screen.",
                        currentScreen.getClass().getName());
                return true;
            }

            Class<?> screenClazz = Class.forName(CLIENT_SCREEN_CLASS);
            Constructor<?> ctor = screenClazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object screenInstance = ctor.newInstance();

            mcClass.getMethod("setScreen", Class.forName("net.minecraft.client.gui.screens.Screen"))
                    .invoke(mc, screenInstance);

            LOG.debug("[Locksmith][IronKeyItem] Opened IronKeyRegisterScreen (reflection).");
            return true;

        } catch (Throwable t) {
            LOG.error("[Locksmith][IronKeyItem] openRegisterScreenClientSafe failed (non-fatal).", t);
            return false;
        }
    }
}
