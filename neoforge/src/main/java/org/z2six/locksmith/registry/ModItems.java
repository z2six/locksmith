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
 */
public final class ModItems {

    private static final Logger LOG = Constants.LOG;

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Constants.MOD_ID);

    // Player-visible item
    public static final DeferredHolder<Item, Item> KEY_IRON = ITEMS.register("iron_key", () ->
            new IronKeyItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)
            )
    );

    // Internal render-only item (NOT added to creative, no recipe). Uses your lock_iron model JSON.
    public static final DeferredHolder<Item, Item> LOCK_IRON = ITEMS.register("lock_iron", () ->
            new Item(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.COMMON)
            )
    );

    private ModItems() {
        // no instances
    }

    public static void debugLogRegisteredItemsSafe() {
        try {
            Item key = KEY_IRON.get();
            Item lock = LOCK_IRON.get();
            LOG.debug("[Locksmith][ModItems] Registered items OK: iron_key={}, lock_iron={}",
                    safeItemName(new ItemStack(key)),
                    safeItemName(new ItemStack(lock)));
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
