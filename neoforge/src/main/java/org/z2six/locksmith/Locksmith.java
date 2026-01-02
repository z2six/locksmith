// MainFile: neoforge/src/main/java/org/z2six/locksmith/Locksmith.java
package org.z2six.locksmith;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.z2six.locksmith.network.LocksmithPayloads;
import org.z2six.locksmith.registry.ModCreativeTabs;
import org.z2six.locksmith.registry.ModItems;

@Mod(Constants.MOD_ID)
public class Locksmith {

    private static final Logger LOG = Constants.LOG;

    public Locksmith(IEventBus eventBus) {
        LOG.info("[Locksmith] Hello NeoForge world! Bootstrapping common init...");
        CommonClass.init();

        // Register item registry
        try {
            ModItems.ITEMS.register(eventBus);
            LOG.info("[Locksmith] Registered DeferredRegister for items.");
        } catch (Throwable t) {
            LOG.error("[Locksmith] FAILED to register item DeferredRegister (this is bad).", t);
        }

        // Payload registration (network)
        try {
            eventBus.addListener(LocksmithPayloads::onRegisterPayloadHandlers);
            LOG.info("[Locksmith] Registered payload handler registration listener.");
        } catch (Throwable t) {
            LOG.error("[Locksmith] FAILED to register payload handler listener (this is bad).", t);
        }

        // Creative tab insertion
        try {
            eventBus.addListener(ModCreativeTabs::onBuildCreativeTabContents);
            LOG.info("[Locksmith] Registered creative tab contents listener.");
        } catch (Throwable t) {
            LOG.error("[Locksmith] FAILED to register creative tab listener (non-fatal).", t);
        }

        ModItems.debugLogRegisteredItemsSafe();
    }
}
