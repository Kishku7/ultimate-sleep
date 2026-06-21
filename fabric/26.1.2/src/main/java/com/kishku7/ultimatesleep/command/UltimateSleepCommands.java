package com.kishku7.ultimatesleep.command;

import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.config.Settings;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The /usleep command tree.
 *
 * Open to all: status, query, afk, auto, yes, no.
 * Tiered (FUNCTIONAL_SPEC.md section 2; checks in SleepPermissions):
 *   /usleep set <key> <value>            -- op 2 or sleep-admin
 *   /usleep admin set <key> <value>      -- op 3 or sleep-admin
 *   /usleep admin afk <player>           -- op 3 or sleep-admin
 *   /usleep admin admins add|remove|list -- op 3 or sleep-admin
 *
 * AFK status messages are sent centrally by AfkManager on any transition, so the afk commands
 * here do not message the affected player themselves.
 *
 * Returns the /usleep root node so /afk can redirect to the "afk" child (AfkCommandManager).
 */
public final class UltimateSleepCommands {

    private UltimateSleepCommands() {}

    public static LiteralCommandNode<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register(Commands.literal("usleep")
                .then(Commands.literal("status").executes(UltimateSleepCommands::status))
                .then(Commands.literal("query").executes(UltimateSleepCommands::query)) // all users
                .then(Commands.literal("afk").executes(UltimateSleepCommands::toggleAfk))
                .then(Commands.literal("auto").executes(UltimateSleepCommands::toggleAuto))
                .then(Commands.literal("yes").executes(ctx -> vote(ctx, true)))
                .then(Commands.literal("no").executes(ctx -> vote(ctx, false)))
                .then(Commands.literal("set")
                        .requires(src -> UltimateSleep.permissions().canSet(src))
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(UltimateSleepCommands::doSet))))
                .then(Commands.literal("admin")
                        .requires(src -> UltimateSleep.permissions().canAdmin(src))
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(UltimateSleepCommands::doSet))))
                        .then(Commands.literal("afk")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(UltimateSleepCommands::adminAfk)))
                        .then(Commands.literal("admins")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .executes(UltimateSleepCommands::adminsAdd)))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .executes(UltimateSleepCommands::adminsRemove)))
                                .then(Commands.literal("list")
                                        .executes(UltimateSleepCommands::adminsList)))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int afkCount = src.getServer() == null ? 0 : UltimateSleep.afk().afkCount(src.getServer());
        src.sendSystemMessage(Component.literal(
                "[Ultimate Sleep] enabled=" + UltimateSleep.settings().bool("enabled")
                        + ", mode=" + UltimateSleep.settings().string("requirement_mode")
                        + ", required=" + UltimateSleep.settings().integer("required_sleep_percentage") + "%"
                        + ", AFK players=" + afkCount));
        return 1;
    }

    /** Full settings dump -- available to ANY user. */
    private static int query(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSystemMessage(Component.literal("[Ultimate Sleep] current settings:"));
        for (Settings.Entry e : UltimateSleep.settings().all().values()) {
            src.sendSystemMessage(Component.literal(
                    "  " + e.key + " = " + e.get() + "  (" + e.type + ") - " + e.description));
        }
        src.sendSystemMessage(Component.literal(
                "  /afk owner = " + UltimateSleep.afkCommands().ownerLabel()));
        return 1;
    }

    private static int toggleAfk(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            src.sendSystemMessage(Component.literal("Only players can toggle AFK."));
            return 0;
        }
        UltimateSleep.afk().toggleManual(p); // AfkManager notifies the player on the next tick
        return 1;
    }

    private static int toggleAuto(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            src.sendSystemMessage(Component.literal("Only players can use auto-sleep."));
            return 0;
        }
        if (!UltimateSleep.settings().bool("auto_sleep_enabled")) {
            src.sendSystemMessage(Component.literal("[Ultimate Sleep] Auto-sleep is disabled by the admin."));
            return 0;
        }
        boolean on = UltimateSleep.autoSleep().toggle(p);
        src.sendSystemMessage(Component.literal("[Ultimate Sleep] Auto-sleep is now "
                + (on ? "ON" : "OFF") + " for you."));
        return 1;
    }

    private static int vote(CommandContext<CommandSourceStack> ctx, boolean yes) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            src.sendSystemMessage(Component.literal("Only players can vote."));
            return 0;
        }
        UltimateSleep.vote().castVote(p, yes);
        return 1;
    }

    /** Shared settings setter for /usleep set and /usleep admin set. */
    private static int doSet(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String key = StringArgumentType.getString(ctx, "key");
        String value = StringArgumentType.getString(ctx, "value");
        String err = UltimateSleep.settings().setRaw(key, value);
        if (err != null) {
            src.sendSystemMessage(Component.literal("[Ultimate Sleep] " + err));
            return 0;
        }
        UltimateSleep.settings().save();
        if (src.getServer() != null) UltimateSleep.engine().applyConfig(src.getServer());
        src.sendSystemMessage(Component.literal("[Ultimate Sleep] set " + key + " = " + value));
        return 1;
    }

    private static int adminAfk(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "player");
        ServerPlayer target = src.getServer() == null
                ? null : src.getServer().getPlayerList().getPlayerByName(name);
        if (target == null) {
            src.sendSystemMessage(Component.literal("[Ultimate Sleep] player not found / offline: " + name));
            return 0;
        }
        UltimateSleep.afk().setManual(target, true); // target notified by AfkManager on next tick
        src.sendSystemMessage(Component.literal("[Ultimate Sleep] set " + name + " AFK."));
        return 1;
    }

    private static int adminsAdd(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "player");
        boolean added = UltimateSleep.permissions().addAdmin(name);
        src.sendSystemMessage(Component.literal(added
                ? "[Ultimate Sleep] " + name + " is now a sleep-admin."
                : "[Ultimate Sleep] " + name + " was already a sleep-admin."));
        return added ? 1 : 0;
    }

    private static int adminsRemove(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "player");
        boolean removed = UltimateSleep.permissions().removeAdmin(name);
        src.sendSystemMessage(Component.literal(removed
                ? "[Ultimate Sleep] removed sleep-admin " + name + "."
                : "[Ultimate Sleep] " + name + " was not a sleep-admin."));
        return removed ? 1 : 0;
    }

    private static int adminsList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        var admins = UltimateSleep.permissions().list();
        src.sendSystemMessage(Component.literal("[Ultimate Sleep] sleep-admins: "
                + (admins.isEmpty() ? "(none)" : String.join(", ", admins))));
        return 1;
    }
}
