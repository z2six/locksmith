// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/profile/ServerLockRenderProfiles.java
package org.z2six.locksmith.render.profile;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.render.LockRenderTuning;

import java.util.Collections;
import java.util.Map;

/**
 * Server-side cache of loaded profiles from config JSON.
 * This is the authoritative source for what clients should render.
 */
public final class ServerLockRenderProfiles {

    private static final Logger LOG = Constants.LOG;

    private static final Object2ObjectOpenHashMap<ResourceLocation, LockRenderProfile> PROFILES = new Object2ObjectOpenHashMap<>();

    private ServerLockRenderProfiles() {
    }

    public static void replaceAll(Map<ResourceLocation, LockRenderProfile> map) {
        try {
            PROFILES.clear();
            if (map != null && !map.isEmpty()) {
                PROFILES.putAll(map);
            }
            LOG.info("[Locksmith][Server] Loaded lock render profiles. count={}", PROFILES.size());
        } catch (Throwable t) {
            LOG.error("[Locksmith][ServerLockRenderProfiles] replaceAll failed (non-fatal).", t);
        }
    }

    public static Map<ResourceLocation, LockRenderProfile> snapshot() {
        try {
            return Collections.unmodifiableMap(new Object2ObjectOpenHashMap<>(PROFILES));
        } catch (Throwable t) {
            LOG.warn("[Locksmith][ServerLockRenderProfiles] snapshot failed (non-fatal).", t);
            return Collections.emptyMap();
        }
    }

    public static boolean isEmpty() {
        try {
            return PROFILES.isEmpty();
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Ensures we have sane defaults even if config is missing/broken.
     * We do NOT attempt to guess all door ids; we provide fallback defaults at render time too.
     */
    public static LockRenderProfile defaultDoorFor(ResourceLocation blockId) {
        return LockRenderProfile.defaultDoor(
                blockId,
                LockRenderTuning.OFFSET_X, LockRenderTuning.OFFSET_Y, LockRenderTuning.OFFSET_Z,
                LockRenderTuning.ROT_X, LockRenderTuning.ROT_Y, LockRenderTuning.ROT_Z,
                LockRenderTuning.SCALE,
                LockRenderTuning.NUDGE_HINGE_LEFT, LockRenderTuning.NUDGE_HINGE_RIGHT
        );
    }
}
