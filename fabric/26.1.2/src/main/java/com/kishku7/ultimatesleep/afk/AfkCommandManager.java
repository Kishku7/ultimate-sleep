package com.kishku7.ultimatesleep.afk;

import com.kishku7.ultimatesleep.UltimateSleep;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Owns the standalone /afk command -- but only when no other mod already
 * provides one. At command-registration time we look for an existing "afk"
 * literal on the dispatcher; if present, we stand down and record that an
 * external mod owns it. The owner is reported by /usleep admin query.
 *
 * KNOWN LIMITATIONS (TODO):
 *  - Brigadier does not record which mod registered a node, so a detected
 *    external /afk can currently only be reported as EXTERNAL. Resolving the
 *    exact owning mod id is future work (e.g. a known-provider lookup table or
 *    a Fabric mod scan).
 *  - Registration order across mods is not guaranteed; this is a best-effort
 *    check at our own callback time. If a conflict is observed in practice we
 *    may move the check to ServerLifecycleEvents.SERVER_STARTED.
 */
public final class AfkCommandManager {

    public enum Owner { NONE, ULTIMATE_SLEEP, EXTERNAL }

    private final AfkManager afk;
    private volatile Owner owner = Owner.NONE;
    private volatile String ownerModId = null; // best-effort; null when unknown

    public AfkCommandManager(AfkManager afk) {
        this.afk = afk;
    }

    public Owner owner() { return owner; }

    public String ownerLabel() {
        return switch (owner) {
            case NONE -> "none (no /afk command registered)";
            case ULTIMATE_SLEEP -> "ultimate_sleep";
            case EXTERNAL -> ownerModId != null ? ownerModId : "external (unknown mod)";
        };
    }

    public void registerAfkCommandIfAbsent(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (dispatcher.getRoot().getChild("afk") != null) {
            owner = Owner.EXTERNAL;
            UltimateSleep.LOGGER.info("[UltimateSleep] /afk already provided by another mod; standing down.");
            return;
        }
        if (!UltimateSleep.settings().bool("provide_afk_command")) {
            owner = Owner.NONE;
            UltimateSleep.LOGGER.info("[UltimateSleep] provide_afk_command is off; not registering /afk.");
            return;
        }
        dispatcher.register(Commands.literal("afk").executes(ctx -> {
            CommandSourceStack src = ctx.getSource();
            ServerPlayer p = src.getPlayer();
            if (p == null) {
                src.sendSystemMessage(Component.literal("Only players can use /afk."));
                return 0;
            }
            boolean nowAfk = afk.toggleManual(p);
            src.sendSystemMessage(Component.literal("You are " + (nowAfk ? "now AFK." : "no longer AFK.")));
            return 1;
        }));
        owner = Owner.ULTIMATE_SLEEP;
        ownerModId = "ultimate_sleep";
        UltimateSleep.LOGGER.info("[UltimateSleep] registered /afk (no other provider found).");
    }
}
