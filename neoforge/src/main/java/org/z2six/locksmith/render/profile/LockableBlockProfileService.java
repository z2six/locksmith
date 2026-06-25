// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/profile/LockableBlockProfileService.java
package org.z2six.locksmith.render.profile;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.config.LockableBlockConfigStore;
import org.z2six.locksmith.network.SyncLockRenderProfilesPayload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LockableBlockProfileService {
    private static final Logger LOG = Constants.LOG;

    private static volatile List<LockableBlockEntry> ENTRIES = List.of();
    private static volatile Map<ResourceLocation, LockRenderProfile> PROFILES = Collections.emptyMap();
    private static volatile boolean LOADED = false;

    private LockableBlockProfileService() {
    }

    public static synchronized void reloadFromDisk() {
        List<LockableBlockEntry> loaded = LockableBlockConfigStore.loadOrCreate();
        applyEntries(loaded);
        LOADED = true;
        LOG.info("[Locksmith][LockableBlockProfileService] Loaded {} editor entries, {} active render profiles.",
                ENTRIES.size(), PROFILES.size());
    }

    public static synchronized SaveResult saveFromEditor(MinecraftServer server, ServerPlayer actor, List<LockableBlockEntry> entries) {
        if (!canEdit(actor)) {
            return new SaveResult(false, "You do not have permission to edit Locksmith lockable blocks.", getValidationSnapshot());
        }

        List<LockableBlockEntry> normalized = normalizeEntries(entries);
        List<LockableBlockValidationResult> validation = validateAll(normalized);
        boolean ok = true;
        for (LockableBlockValidationResult result : validation) {
            if (result != null && !result.okForSave()) {
                ok = false;
                break;
            }
        }

        if (!ok) {
            return new SaveResult(false, "Save rejected. Fix invalid lockable block entries first.", validation);
        }

        try {
            LockableBlockConfigStore.saveAtomically(normalized);
            applyEntries(normalized);
            LOADED = true;
            syncProfilesToAll(server);
            return new SaveResult(true, "Lockable block config saved and synced.", getValidationSnapshot());
        } catch (IOException e) {
            LOG.error("[Locksmith][LockableBlockProfileService] Failed to save lockable block config.", e);
            return new SaveResult(false, "Failed to save server config: " + e.getMessage(), validation);
        }
    }

    public static List<LockableBlockEntry> getEntriesSnapshot() {
        ensureLoaded();
        return new ArrayList<>(ENTRIES);
    }

    public static Map<ResourceLocation, LockRenderProfile> getProfilesForNetwork() {
        ensureLoaded();
        return new Object2ObjectOpenHashMap<>(PROFILES);
    }

    public static Map<ResourceLocation, LockRenderProfile> getCachedProfiles() {
        ensureLoaded();
        return Collections.unmodifiableMap(PROFILES);
    }

    public static List<LockableBlockValidationResult> getValidationSnapshot() {
        return validateAll(getEntriesSnapshot());
    }

    public static List<LockableBlockValidationResult> validateAll(List<LockableBlockEntry> entries) {
        List<LockableBlockEntry> safe = entries == null ? List.of() : entries;
        HashMap<ResourceLocation, Integer> counts = new HashMap<>();
        for (LockableBlockEntry entry : safe) {
            if (entry != null && entry.enabled() && entry.blockId() != null) {
                counts.merge(entry.blockId(), 1, Integer::sum);
            }
        }

        ArrayList<LockableBlockValidationResult> out = new ArrayList<>(safe.size());
        for (LockableBlockEntry entry : safe) {
            out.add(validateOne(entry, counts));
        }
        return out;
    }

    public static boolean canEdit(ServerPlayer player) {
        try {
            if (player == null || player.getServer() == null) return false;
            if (player.hasPermissions(2)) return true;
            return player.getServer().getPlayerList().isOp(player.getGameProfile());
        } catch (Throwable t) {
            LOG.warn("[Locksmith][LockableBlockProfileService] canEdit failed (non-fatal).", t);
            return false;
        }
    }

    public static void syncProfilesToAll(MinecraftServer server) {
        try {
            if (server == null || server.getPlayerList() == null) return;
            Map<ResourceLocation, LockRenderProfile> profiles = getProfilesForNetwork();
            for (ServerPlayer target : server.getPlayerList().getPlayers()) {
                PacketDistributor.sendToPlayer(target, new SyncLockRenderProfilesPayload(profiles));
            }
            LOG.info("[Locksmith][LockableBlockProfileService] Hot-synced {} profiles to {} player(s).",
                    profiles.size(), server.getPlayerList().getPlayerCount());
        } catch (Throwable t) {
            LOG.error("[Locksmith][LockableBlockProfileService] syncProfilesToAll failed (non-fatal).", t);
        }
    }

    public static boolean isLockableChest(ResourceLocation blockId) {
        try {
            if (blockId == null) return false;
            LockRenderProfile profile = getCachedProfiles().get(blockId);
            return profile != null && profile.isValid() && profile.type == LockTargetType.CHEST;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][LockableBlockProfileService] isLockableChest failed (non-fatal). blockId={}", blockId, t);
            return false;
        }
    }

    public static boolean isLockableDoor(ResourceLocation blockId) {
        try {
            if (blockId == null) return false;
            LockRenderProfile profile = getCachedProfiles().get(blockId);
            return profile != null && profile.isValid() && profile.type == LockTargetType.DOOR;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][LockableBlockProfileService] isLockableDoor failed (non-fatal). blockId={}", blockId, t);
            return false;
        }
    }

    public static boolean isLockableGeneric(ResourceLocation blockId) {
        try {
            if (blockId == null) return false;
            LockRenderProfile profile = getCachedProfiles().get(blockId);
            return profile != null && profile.isValid() && profile.type == LockTargetType.GENERIC;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][LockableBlockProfileService] isLockableGeneric failed (non-fatal). blockId={}", blockId, t);
            return false;
        }
    }

    private static void ensureLoaded() {
        if (!LOADED) {
            reloadFromDisk();
        }
    }

    private static void applyEntries(List<LockableBlockEntry> entries) {
        List<LockableBlockEntry> normalized = normalizeEntries(entries);
        Object2ObjectOpenHashMap<ResourceLocation, LockRenderProfile> map = new Object2ObjectOpenHashMap<>();
        List<LockableBlockValidationResult> validation = validateAll(normalized);
        for (int i = 0; i < normalized.size(); i++) {
            LockableBlockEntry entry = normalized.get(i);
            LockableBlockValidationResult result = i < validation.size() ? validation.get(i) : null;
            if (entry != null && entry.enabled() && result != null && result.status() == LockableBlockValidationStatus.OK) {
                map.put(entry.blockId(), entry.toRenderProfile());
            }
        }
        ENTRIES = Collections.unmodifiableList(normalized);
        PROFILES = Collections.unmodifiableMap(map);
    }

    private static List<LockableBlockEntry> normalizeEntries(List<LockableBlockEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return LockableBlockDefaults.create();
        }

        ArrayList<LockableBlockEntry> out = new ArrayList<>(entries.size());
        for (LockableBlockEntry entry : entries) {
            if (entry == null) continue;
            LockTransform transform = entry.transform() == null ? null : entry.transform().clamped();
            out.add(new LockableBlockEntry(entry.blockId(), entry.type(), transform, entry.builtinDefault(), entry.enabled()));
        }
        return out;
    }

    private static LockableBlockValidationResult validateOne(LockableBlockEntry entry, Map<ResourceLocation, Integer> counts) {
        if (entry == null || entry.blockId() == null) {
            return new LockableBlockValidationResult(null, LockableBlockValidationStatus.INVALID_ID, "Block id is missing or invalid.");
        }

        ResourceLocation blockId = entry.blockId();
        if (!entry.enabled()) {
            return new LockableBlockValidationResult(blockId, LockableBlockValidationStatus.DISABLED, "Entry is saved but disabled.");
        }

        if (counts.getOrDefault(blockId, 0) > 1) {
            return new LockableBlockValidationResult(blockId, LockableBlockValidationStatus.DUPLICATE_BLOCK, "Duplicate enabled entry for " + blockId + ".");
        }

        LockTargetType type = entry.type();
        if (type == null) {
            return new LockableBlockValidationResult(blockId, LockableBlockValidationStatus.UNSUPPORTED_TYPE, "Unsupported target type.");
        }

        Block block = BuiltInRegistries.BLOCK.get(blockId);
        ResourceLocation resolvedId = BuiltInRegistries.BLOCK.getKey(block);
        if (block == null || resolvedId == null || !resolvedId.equals(blockId)) {
            return new LockableBlockValidationResult(blockId, LockableBlockValidationStatus.UNKNOWN_BLOCK, "No block is registered for " + blockId + ".");
        }

        if (type == LockTargetType.DOOR) {
            if (!(block instanceof DoorBlock)) {
                return new LockableBlockValidationResult(blockId, LockableBlockValidationStatus.TYPE_MISMATCH, blockId + " is not a door block.");
            }
            return new LockableBlockValidationResult(blockId, LockableBlockValidationStatus.OK, "Door entry is active.");
        }

        if (type == LockTargetType.CHEST) {
            if (!(block instanceof ChestBlock)) {
                return new LockableBlockValidationResult(blockId, LockableBlockValidationStatus.TYPE_MISMATCH, blockId + " is not a chest block.");
            }

            BlockState state = block.defaultBlockState();
            if (state == null || !state.hasProperty(ChestBlock.FACING) || !state.hasProperty(ChestBlock.TYPE)) {
                return new LockableBlockValidationResult(blockId, LockableBlockValidationStatus.MISSING_PROPERTIES,
                        blockId + " is missing chest facing/type properties.");
            }
            return new LockableBlockValidationResult(blockId, LockableBlockValidationStatus.OK, "Chest entry is active.");
        }

        if (type == LockTargetType.GENERIC) {
            return new LockableBlockValidationResult(blockId, LockableBlockValidationStatus.OK, "Other entry is active as a single-block lock target.");
        }

        return new LockableBlockValidationResult(blockId, LockableBlockValidationStatus.UNSUPPORTED_TYPE, "Unsupported target type.");
    }

    public record SaveResult(boolean success, String message, List<LockableBlockValidationResult> validationResults) {
    }
}
