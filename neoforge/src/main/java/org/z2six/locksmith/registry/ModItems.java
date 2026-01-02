// MainFile: neoforge/src/main/java/org/z2six/locksmith/registry/ModItems.java
package org.z2six.locksmith.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.item.IronKeyItem;

/**
 * NeoForge-side item registry.
 * NeoForge 1.21.x uses DeferredHolder.
 */
public final class ModItems {

    private static final Logger LOG = Constants.LOG;

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Constants.MOD_ID);

    // Keep field name KEY_IRON because you already use it elsewhere.
    public static final DeferredHolder<Item, Item> KEY_IRON = ITEMS.register("iron_key", () ->
            new IronKeyItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)
            )
    );

    private ModItems() {
        // no instances
    }

    public static void debugLogRegisteredItemsSafe() {
        try {
            Item item = KEY_IRON.get();
            LOG.debug("[Locksmith][ModItems] Registered item OK: iron_key={}", safeItemName(new ItemStack(item)));
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ModItems] debugLogRegisteredItemsSafe failed (non-fatal).", t);
        }
    }

    private static String safeItemName(ItemStack stack) {
        try {
            return stack.getItem().toString();
        } catch (Throwable t) {
            return "<unknown>";
        }
    }
}
