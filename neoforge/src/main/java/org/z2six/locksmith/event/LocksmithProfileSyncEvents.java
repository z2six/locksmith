// neoforge/src/main/java/org/z2six/locksmith/event/LocksmithProfileSyncEvents.java
package org.z2six.locksmith.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.network.SyncLockRenderProfilesPayload;
import org.z2six.locksmith.render.profile.ServerLockRenderProfiles;

public final class LocksmithProfileSyncEvents {

    private static final Logger LOG = Constants.LOG;

    private LocksmithProfileSyncEvents() {
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (event == null) return;
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;

            ServerLevel level = sp.serverLevel();
            if (level == null) return;

            var profiles = ServerLockRenderProfiles.getProfilesForNetwork();
            PacketDistributor.sendToPlayer(sp, new SyncLockRenderProfilesPayload(profiles));

            LOG.info("[Locksmith] Sent SyncLockRenderProfilesPayload to {} (count={})",
                    sp.getName().getString(),
                    (profiles == null ? 0 : profiles.size()));
        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithProfileSyncEvents] onPlayerLoggedIn failed (non-fatal).", t);
        }
    }
}
