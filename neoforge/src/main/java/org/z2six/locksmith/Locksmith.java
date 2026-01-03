// MainFile: neoforge/src/main/java/org/z2six/locksmith/Locksmith.java
package org.z2six.locksmith;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.z2six.locksmith.config.LockProfileConfig;
import org.z2six.locksmith.event.LocksmithDoorEvents;
import org.z2six.locksmith.network.LocksmithPayloads;
import org.z2six.locksmith.registry.ModCreativeTabs;
import org.z2six.locksmith.registry.ModItems;
import org.z2six.locksmith.config.LockProfileConfig;
import org.z2six.locksmith.config.LocksmithClientConfig;

@Mod(Constants.MOD_ID)
public class Locksmith {

    private static final Logger LOG = Constants.LOG;

    public Locksmith(IEventBus eventBus) {
        LOG.info("[Locksmith] Hello NeoForge world! Bootstrapping common init...");
        CommonClass.init();

        // Ensure the server-authoritative lock profile JSON exists as early as possible
        try {
            LockProfileConfig.ensureDefaultFileExists();
            LOG.info("[Locksmith] Ensured lock profile config exists at startup.");
        } catch (Throwable t) {
            LOG.error("[Locksmith] FAILED to ensure lock profile config exists at startup (non-fatal).", t);
        }

        try {
            LocksmithClientConfig.loadOrCreate();
            LOG.info(
                    "[Locksmith] Loaded client QoL config: autoCloseEnabled={} autoCloseTicks={}",
                    LocksmithClientConfig.isAutoCloseEnabled(),
                    LocksmithClientConfig.getAutoCloseTicks()
            );
        } catch (Throwable t) {
            LOG.error("[Locksmith] FAILED to load client QoL config (non-fatal, using defaults).", t);
        }

        try {
            ModItems.ITEMS.register(eventBus);
            LOG.info("[Locksmith] Registered DeferredRegister for items.");
        } catch (Throwable t) {
            LOG.error("[Locksmith] FAILED to register item DeferredRegister (this is bad).", t);
        }

        try {
            eventBus.addListener(LocksmithPayloads::onRegisterPayloadHandlers);
            LOG.info("[Locksmith] Registered payload handler registration listener.");
        } catch (Throwable t) {
            LOG.error("[Locksmith] FAILED to register payload handler listener (this is bad).", t);
        }

        try {
            eventBus.addListener(ModCreativeTabs::onBuildCreativeTabContents);
            LOG.info("[Locksmith] Registered creative tab contents listener.");
        } catch (Throwable t) {
            LOG.error("[Locksmith] FAILED to register creative tab listener (non-fatal).", t);
        }

        try {
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LocksmithDoorEvents::onRightClickBlock);

            NeoForge.EVENT_BUS.addListener(LocksmithDoorEvents::onPlayerLoggedIn);

            NeoForge.EVENT_BUS.addListener(LocksmithDoorEvents::onBlockBreak);
            NeoForge.EVENT_BUS.addListener(LocksmithDoorEvents::onExplosionDetonate);
            NeoForge.EVENT_BUS.addListener(LocksmithDoorEvents::onServerTick);

            LOG.info("[Locksmith] Registered door lock gameplay events (and cleanup hooks).");
        } catch (Throwable t) {
            LOG.error("[Locksmith] FAILED to register gameplay events (this is bad).", t);
        }

        try {
            if (FMLEnvironment.dist.isClient()) {
                Class<?> clazz = Class.forName("org.z2six.locksmith.client.ClientInit");
                clazz.getMethod("init").invoke(null);
            }
        } catch (Throwable t) {
            LOG.error("[Locksmith] Client init failed (non-fatal).", t);
        }

        ModItems.debugLogRegisteredItemsSafe();
    }
}
