// neoforge/src/main/java/org/z2six/locksmith/registry/ModItems.java
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

public final class ModItems {

    private static final Logger LOG = Constants.LOG;

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Constants.MOD_ID);

    public static final DeferredHolder<Item, Item> KEY_IRON = ITEMS.register("iron_key", () ->
            new IronKeyItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.UNCOMMON)
            )
    );

    public static final DeferredHolder<Item, Item> LOCK_IRON = ITEMS.register("lock_iron", () ->
            new Item(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.COMMON)
            )
    );

    // Internal-only item used for the "unlocked" lock model (lock_iron_unlocked.json).
    // Not added to any creative tab.
    public static final DeferredHolder<Item, Item> LOCK_IRON_UNLOCKED = ITEMS.register("lock_iron_unlocked", () ->
            new Item(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.COMMON)
            )
    );

    private ModItems() {
    }

    public static void debugLogRegisteredItemsSafe() {
        try {
            if (!isBoundSafe(KEY_IRON) || !isBoundSafe(LOCK_IRON)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][ModItems] debugLogRegisteredItemsSafe: DeferredHolders not bound yet; skipping.");
                }
                return;
            }

            Item key = KEY_IRON.get();
            Item lock = LOCK_IRON.get();

            LOG.debug("[Locksmith][ModItems] Registered items OK: iron_key={}, lock_iron={}",
                    safeItemName(new ItemStack(key)),
                    safeItemName(new ItemStack(lock)));

            try {
                int baseKeyMax = key.getMaxStackSize(new ItemStack(key));
                int baseLockMax = lock.getMaxStackSize(new ItemStack(lock));
                LOG.debug("[Locksmith][ModItems] Base max stack sizes: iron_key(base)={} lock_iron(base)={}",
                        baseKeyMax, baseLockMax);
            } catch (Throwable t) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[Locksmith][ModItems] Base max stack size diagnostics failed (non-fatal).", t);
                }
            }

        } catch (Throwable t) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("[Locksmith][ModItems] debugLogRegisteredItemsSafe skipped due to early registry state (non-fatal).", t);
            }
        }
    }

    private static boolean isBoundSafe(Object holder) {
        try {
            if (!(holder instanceof DeferredHolder<?, ?> dh)) return false;

            try {
                var m = dh.getClass().getMethod("isBound");
                Object res = m.invoke(dh);
                if (res instanceof Boolean b) return b;
            } catch (Throwable ignored) {
            }
            try {
                dh.get();
                return true;
            } catch (Throwable t) {
                return false;
            }
        } catch (Throwable t) {
            return false;
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
