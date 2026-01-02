// MainFile: neoforge/src/main/java/org/z2six/locksmith/util/ItemStackDataUtil.java
package org.z2six.locksmith.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

/**
 * Utility for reading/writing mod data on ItemStacks in MC 1.21+ using DataComponents.CUSTOM_DATA.
 *
 * We store our mod fields inside the stack's CustomData (CompoundTag).
 * Defensive: never crash; log and return safe defaults.
 */
public final class ItemStackDataUtil {

    private static final Logger LOG = Constants.LOG;

    private ItemStackDataUtil() {
        // no-op
    }

    public static String getString(ItemStack stack, String key) {
        try {
            if (stack == null || stack.isEmpty()) return "";

            CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
            if (cd == null) return "";

            CompoundTag tag = cd.copyTag();
            if (tag == null || tag.isEmpty()) return "";

            if (!tag.contains(key, Tag.TAG_STRING)) return "";
            String v = tag.getString(key);
            return v == null ? "" : v;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ItemStackDataUtil] getString failed (non-fatal). key={}", key, t);
            return "";
        }
    }

    public static boolean hasNonBlankString(ItemStack stack, String key) {
        try {
            String v = getString(stack, key);
            return v != null && !v.isBlank();
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ItemStackDataUtil] hasNonBlankString failed (non-fatal). key={}", key, t);
            return false;
        }
    }

    public static boolean putString(ItemStack stack, String key, String value) {
        try {
            if (stack == null || stack.isEmpty()) return false;
            if (key == null || key.isBlank()) return false;

            String safeValue = (value == null) ? "" : value;

            CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
            CompoundTag tag = (existing == null) ? new CompoundTag() : existing.copyTag();
            if (tag == null) tag = new CompoundTag();

            tag.putString(key, safeValue);

            // Write back to component
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            return true;
        } catch (Throwable t) {
            LOG.error("[Locksmith][ItemStackDataUtil] putString failed (non-fatal). key={}", key, t);
            return false;
        }
    }
}
