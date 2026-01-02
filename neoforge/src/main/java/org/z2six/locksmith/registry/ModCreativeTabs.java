// MainFile: neoforge/src/main/java/org/z2six/locksmith/registry/ModCreativeTabs.java
package org.z2six.locksmith.registry;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

/**
 * Adds Locksmith items to creative tabs.
 */
public final class ModCreativeTabs {

    private static final Logger LOG = Constants.LOG;

    private ModCreativeTabs() {
        // no instances
    }

    public static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        try {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(ModItems.KEY_IRON.get());
                LOG.debug("[Locksmith][CreativeTabs] Added Iron Key to TOOLS_AND_UTILITIES.");
            }
        } catch (Throwable t) {
            LOG.warn("[Locksmith][CreativeTabs] Failed to add items to creative tab (non-fatal).", t);
        }
    }
}
