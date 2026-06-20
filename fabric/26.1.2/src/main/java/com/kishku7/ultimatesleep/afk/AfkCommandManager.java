package com.kishku7.ultimatesleep.afk;

import com.kishku7.ultimatesleep.UltimateSleep;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Registers the standalone /afk command -- but only when no other mod already
 * provides one. /afk is a Brigadier REDIRECT alias to the /usleep afk node, so
 * it always behaves identically to /usleep afk (single source of truth).
 *
 * At command-registration time we look for an existing "afk" literal on the
 * dispatcher; if present, we stand down and record that an external mod owns it.
 * The owner is reported by /usleep admin query.
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

    /**
     * Register /afk as a redirect alias to the given /usleep afk node, unless an
     * external /afk already exists or the feature is disabled.
     */
    public void registerAfkCommandIfAbsent(CommandDispatcher<CommandSourceStack> dispatcher,
                                           CommandNode<CommandSourceStack> usleepAfkNode) {
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
        dispatcher.register(Commands.literal("afk").redirect(usleepAfkNode));
        owner = Owner.ULTIMATE_SLEEP;
        ownerModId = "ultimate_sleep";
        UltimateSleep.LOGGER.info("[UltimateSleep] registered /afk as an alias to /usleep afk (no other provider found).");
    }
}
