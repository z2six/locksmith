// MainFile: neoforge/src/main/java/org/z2six/locksmith/command/LocksmithCommands.java
package org.z2six.locksmith.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.network.OpenLockableBlocksEditorPayload;
import org.z2six.locksmith.render.profile.LockTargetType;
import org.z2six.locksmith.render.profile.LockableBlockEntry;
import org.z2six.locksmith.render.profile.LockableBlockProfileService;

import java.util.ArrayList;
import java.util.List;

public final class LocksmithCommands {
    private static final Logger LOG = Constants.LOG;

    private LocksmithCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        try {
            CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
            dispatcher.register(Commands.literal("locksmith")
                    .then(Commands.literal("editor")
                            .requires(source -> source.hasPermission(2))
                            .executes(ctx -> openEditor(ctx.getSource())))
                    .then(Commands.literal("add")
                            .requires(source -> source.hasPermission(2))
                            .then(Commands.literal("chest")
                                    .executes(ctx -> openEditorWithHeldBlock(ctx.getSource(), LockTargetType.CHEST)))
                            .then(Commands.literal("door")
                                    .executes(ctx -> openEditorWithHeldBlock(ctx.getSource(), LockTargetType.DOOR)))
                            .then(Commands.literal("generic")
                                    .executes(ctx -> openEditorWithHeldBlock(ctx.getSource(), LockTargetType.GENERIC)))));
            LOG.info("[Locksmith] Registered /locksmith editor and /locksmith add commands.");
        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithCommands] Command registration failed (non-fatal).", t);
        }
    }

    private static int openEditor(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            if (!LockableBlockProfileService.canEdit(player)) {
                source.sendFailure(Component.literal("You do not have permission to edit Locksmith lockable blocks."));
                return 0;
            }

            PacketDistributor.sendToPlayer(player, new OpenLockableBlocksEditorPayload(
                    LockableBlockProfileService.getEntriesSnapshot(),
                    LockableBlockProfileService.getValidationSnapshot()
            ));
            source.sendSuccess(() -> Component.literal("Opening Locksmith lockable block editor."), false);
            return 1;
        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithCommands] Failed to open editor (non-fatal).", t);
            source.sendFailure(Component.literal("Failed to open Locksmith lockable block editor."));
            return 0;
        }
    }

    private static int openEditorWithHeldBlock(CommandSourceStack source, LockTargetType type) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            if (!LockableBlockProfileService.canEdit(player)) {
                source.sendFailure(Component.literal("You do not have permission to edit Locksmith lockable blocks."));
                return 0;
            }

            ItemStack held = player.getMainHandItem();
            if (held.isEmpty() || !(held.getItem() instanceof BlockItem blockItem)) {
                source.sendFailure(Component.literal("Hold a block item in your main hand, then run /locksmith add <chest|door|generic>."));
                return 0;
            }

            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
            if (blockId == null) {
                source.sendFailure(Component.literal("The held block is not registered."));
                return 0;
            }

            List<LockableBlockEntry> entries = new ArrayList<>(LockableBlockProfileService.getEntriesSnapshot());
            LockableBlockEntry entry = new LockableBlockEntry(blockId, type, null, false, true);
            entries.add(entry);
            int selectedIndex = entries.size() - 1;

            PacketDistributor.sendToPlayer(player, new OpenLockableBlocksEditorPayload(
                    entries,
                    LockableBlockProfileService.validateAll(entries),
                    selectedIndex
            ));
            source.sendSuccess(() -> Component.literal("Opening Locksmith editor with unsaved entry for " + blockId + "."), false);
            return 1;
        } catch (Throwable t) {
            LOG.error("[Locksmith][LocksmithCommands] Failed to open editor with held block (non-fatal).", t);
            source.sendFailure(Component.literal("Failed to open Locksmith lockable block editor."));
            return 0;
        }
    }
}
