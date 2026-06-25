// MainFile: neoforge/src/main/java/org/z2six/locksmith/Locksmith.java
package org.z2six.locksmith;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.z2six.locksmith.client.ClientModBusEvents;
import org.z2six.locksmith.config.LocksmithClientConfig;
import org.z2six.locksmith.command.LocksmithCommands;
import org.z2six.locksmith.event.LocksmithChestEvents;
import org.z2six.locksmith.event.LocksmithCuriosEvents;
import org.z2six.locksmith.event.LocksmithDoorEvents;
import org.z2six.locksmith.event.LocksmithGenericEvents;
import org.z2six.locksmith.event.LocksmithProfileSyncEvents;
import org.z2six.locksmith.network.LocksmithPayloads;
import org.z2six.locksmith.registry.ModCreativeTabs;
import org.z2six.locksmith.registry.ModItems;
import org.z2six.locksmith.registry.ModRecipeSerializers;
import org.z2six.locksmith.render.profile.LockableBlockProfileService;

@Mod(Constants.MOD_ID)
public class Locksmith {
    private static final Logger LOG = Constants.LOG;

    public Locksmith(IEventBus eventBus) {
        LOG.info("[Locksmith] Hello NeoForge world! Bootstrapping common init...");
        CommonClass.init();

        try {
            LockableBlockProfileService.reloadFromDisk();
            LOG.info("[Locksmith] Loaded lockable block profile service at startup.");
        } catch (Throwable t) {
            LOG.error("[Locksmith] FAILED to load lockable block profile service at startup (non-fatal).", t);
        }

        try {
            // NOTE: this is a client config; calling it on dedicated server should be harmless if your implementation is safe.
            // If it isn't, you should move this into client setup later. For now we keep your behavior unchanged.
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
            ModRecipeSerializers.RECIPE_SERIALIZERS.register(eventBus);
            LOG.info("[Locksmith] Registered DeferredRegister for recipe serializers.");
        } catch (Throwable t) {
            LOG.error("[Locksmith] FAILED to register recipe serializer DeferredRegister (this is bad).", t);
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
            NeoForge.EVENT_BUS.addListener(LocksmithCommands::onRegisterCommands);
            LOG.info("[Locksmith] Registered command listener.");
        } catch (Throwable t) {
            LOG.error("[Locksmith] FAILED to register command listener (non-fatal).", t);
        }

        try {
            // Doors
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LocksmithDoorEvents::onRightClickBlock);
            NeoForge.EVENT_BUS.addListener(LocksmithDoorEvents::onPlayerLoggedIn);
            NeoForge.EVENT_BUS.addListener(LocksmithDoorEvents::onBlockBreak);
            NeoForge.EVENT_BUS.addListener(LocksmithDoorEvents::onExplosionDetonate);
            NeoForge.EVENT_BUS.addListener(LocksmithDoorEvents::onServerTick);

            // Chests
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LocksmithChestEvents::onRightClickBlock);
            NeoForge.EVENT_BUS.addListener(LocksmithChestEvents::onPlayerLoggedIn);
            NeoForge.EVENT_BUS.addListener(LocksmithChestEvents::onBlockBreak);
            NeoForge.EVENT_BUS.addListener(LocksmithChestEvents::onExplosionDetonate);

            // Generic single-block interactables
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LocksmithGenericEvents::onRightClickBlock);
            NeoForge.EVENT_BUS.addListener(LocksmithGenericEvents::onPlayerLoggedIn);
            NeoForge.EVENT_BUS.addListener(LocksmithGenericEvents::onBlockBreak);
            NeoForge.EVENT_BUS.addListener(LocksmithGenericEvents::onExplosionDetonate);

            // Profiles -> client cache (used for both render + type gating client-side)
            NeoForge.EVENT_BUS.addListener(LocksmithProfileSyncEvents::onPlayerLoggedIn);

            LOG.info("[Locksmith] Registered door + chest + generic lock gameplay events (and cleanup hooks).");
        } catch (Throwable t) {
            LOG.error("[Locksmith] FAILED to register gameplay events (this is bad).", t);
        }

        // Curios (optional) - server-side enforcement + quick-equip support
        try {
            NeoForge.EVENT_BUS.addListener(LocksmithCuriosEvents::onPlayerTickPost);
            LOG.info("[Locksmith] Registered optional Curios enforcement tick handler.");
        } catch (Throwable t) {
            LOG.error("[Locksmith] FAILED to register Curios enforcement tick handler (non-fatal).", t);
        }

        // ✅ Correct client init wiring:
        // Register client-only listeners on the MOD event bus using FML client setup.
        try {
            if (FMLEnvironment.dist.isClient()) {
                ClientModBusEvents.register(eventBus);
                LOG.info("[Locksmith] Registered client MOD-bus events (FMLClientSetupEvent).");
            }
        } catch (Throwable t) {
            LOG.error("[Locksmith] Client MOD-bus registration failed (non-fatal).", t);
        }

        // Client-only: Shift+RMB quick-equip into Curios key slot (optional integration)
        try {
            if (FMLEnvironment.dist.isClient()) {
                // Use reflection so this class is never linked on dedicated server.
                Class<?> clazz = Class.forName("org.z2six.locksmith.client.ClientCuriosKeyEquipEvents");
                clazz.getMethod("register").invoke(null);
                LOG.info("[Locksmith] Registered client Curios quick-equip screen listener (reflection).");
            }
        } catch (Throwable t) {
            LOG.debug("[Locksmith] Client Curios quick-equip screen listener not registered (non-fatal).", t);
        }

        // Optional Curios compat bootstrap (currently just logs; kept as a hook point)
        try {
            if (ModList.get().isLoaded("curios")) {
                LOG.info("[Locksmith] Curios detected: optional key-slot integration enabled.");
            } else {
                LOG.info("[Locksmith] Curios not detected: running without Curios integration.");
            }
        } catch (Throwable t) {
            LOG.debug("[Locksmith] Curios detection failed (non-fatal).", t);
        }

        ModItems.debugLogRegisteredItemsSafe();
    }
}
