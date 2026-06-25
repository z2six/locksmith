// MainFile: neoforge/src/main/java/org/z2six/locksmith/command/LocksmithCommands.java
package org.z2six.locksmith.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.network.OpenLockableBlocksEditorPayload;
import org.z2six.locksmith.render.profile.LockableBlockProfileService;

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
                            .executes(ctx -> openEditor(ctx.getSource()))));
            LOG.info("[Locksmith] Registered /locksmith editor command.");
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
}
