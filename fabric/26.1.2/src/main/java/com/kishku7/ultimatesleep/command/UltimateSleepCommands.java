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
import net.minecraft.server.permissions.Permissions;

/**
 * The /usleep command tree.
 *
 *   /usleep status                       -- short status line (any user)
 *   /usleep query                        -- full settings dump + /afk owner (ANY user)
 *   /usleep afk                          -- toggle your own AFK (any user)
 *   /usleep admin set <key> <value>      -- change a setting (OP level 4)
 *   /usleep admin afk <player>           -- force a player into AFK (OP level 4)
 *
 * Permission tiers (FUNCTIONAL_SPEC.md section 2):
 *   - status / query / afk + vote/auto commands: open to all.
 *   - /usleep set ... (NOT yet wired): the "special permission" tier -- sleep-admin roster,
 *     permission node, or op level 3 (COMMANDS_ADMIN). Roadmap step 2.
 *   - /usleep admin ...: OP level 4 (COMMANDS_OWNER) ONLY -- the master tier; the only place
 *     the sleep-admin roster will be managed.
 *
 * Returns the registered /usleep root node so the standalone /afk alias can redirect to the
 * "afk" child (see AfkCommandManager).
 */
public final class UltimateSleepCommands {

    private UltimateSleepCommands() {}

    public static LiteralCommandNode<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register(Commands.literal("usleep")
                .then(Commands.literal("status").executes(UltimateSleepCommands::status))
                .then(Commands.literal("query").executes(UltimateSleepCommands::query)) // all users
                .then(Commands.literal("afk").executes(UltimateSleepCommands::toggleAfk))
                .then(Commands.literal("admin")
                        // OP level 4 only.
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_OWNER))
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(UltimateSleepCommands::adminSet))))
                        .then(Commands.literal("afk")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(UltimateSleepCommands::adminAfk)))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int afkCount = src.getServer() == null ? 0 : UltimateSleep.afk().afkCount(src.getServer());
        src.sendSystemMessage(Component.literal(
                "[Ultimate Sleep] enabled=" + UltimateSleep.settings().bool("enabled")
                        + ", required=" + UltimateSleep.settings().integer("required_sleep_percentage") + "%"
                        + ", AFK players=" + afkCount));
        return 1;
    }

    /** Full settings dump -- available to ANY user so everyone can see how the mod is configured. */
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
        boolean nowAfk = UltimateSleep.afk().toggleManual(p);
        src.sendSystemMessage(Component.literal("You are " + (nowAfk ? "now AFK." : "no longer AFK.")));
        return 1;
    }

    private static int adminSet(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String key = StringArgumentType.getString(ctx, "key");
        String value = StringArgumentType.getString(ctx, "value");
        String err = UltimateSleep.settings().setRaw(key, value);
        if (err != null) {
            src.sendSystemMessage(Component.literal("[Ultimate Sleep] " + err));
            return 0;
        }
        src.sendSystemMessage(Component.literal("[Ultimate Sleep] set " + key + " = " + value));
        return 1;
    }

    /** Force a named player into AFK (OP 4). Uses getPlayerByName (must be online). */
    private static int adminAfk(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "player");
        ServerPlayer target = src.getServer() == null
                ? null : src.getServer().getPlayerList().getPlayerByName(name);
        if (target == null) {
            src.sendSystemMessage(Component.literal("[Ultimate Sleep] player not found / offline: " + name));
            return 0;
        }
        UltimateSleep.afk().setManual(target, true);
        src.sendSystemMessage(Component.literal("[Ultimate Sleep] set " + name + " AFK."));
        target.sendSystemMessage(Component.literal("[Ultimate Sleep] An admin set you AFK."));
        return 1;
    }
}
