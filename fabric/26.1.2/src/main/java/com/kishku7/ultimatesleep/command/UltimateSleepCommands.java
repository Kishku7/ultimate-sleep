package com.kishku7.ultimatesleep.command;

import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.config.Settings;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The /usleep command tree.
 *
 *   /usleep status                    -- player-facing summary
 *   /usleep afk                       -- toggle your own AFK (always available)
 *   /usleep admin query               -- dump all settings + /afk owner (perm 2)
 *   /usleep admin set &lt;key&gt; &lt;value&gt;   -- change a setting (perm 2)
 *
 * The future client admin panel drives this: on open it issues
 * "/usleep admin query" to learn the current settings and which mod owns the
 * /afk command, then sends "/usleep admin set &lt;key&gt; &lt;value&gt;" per change.
 */
public final class UltimateSleepCommands {

    private UltimateSleepCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("usleep")
                .then(Commands.literal("status").executes(UltimateSleepCommands::status))
                .then(Commands.literal("afk").executes(UltimateSleepCommands::toggleAfk))
                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("query").executes(UltimateSleepCommands::adminQuery))
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(UltimateSleepCommands::adminSet))))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int afkCount = src.getServer() == null ? 0 : UltimateSleep.afk().afkCount(src.getServer());
        src.sendSuccess(() -> Component.literal(
                "[Ultimate Sleep] enabled=" + UltimateSleep.settings().bool("enabled")
                        + ", required=" + UltimateSleep.settings().integer("required_sleep_percentage") + "%"
                        + ", AFK players=" + afkCount), false);
        return 1;
    }

    private static int toggleAfk(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            boolean nowAfk = UltimateSleep.afk().toggleManual(p);
            ctx.getSource().sendSuccess(
                    () -> Component.literal("You are " + (nowAfk ? "now AFK." : "no longer AFK.")), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Only players can toggle AFK."));
            return 0;
        }
    }

    private static int adminQuery(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("[Ultimate Sleep] current settings:"), false);
        for (Settings.Entry e : UltimateSleep.settings().all().values()) {
            src.sendSuccess(() -> Component.literal(
                    "  " + e.key + " = " + e.get() + "  (" + e.type + ") - " + e.description), false);
        }
        src.sendSuccess(() -> Component.literal(
                "  /afk owner = " + UltimateSleep.afkCommands().ownerLabel()), false);
        return 1;
    }

    private static int adminSet(CommandContext<CommandSourceStack> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        String value = StringArgumentType.getString(ctx, "value");
        String err = UltimateSleep.settings().setRaw(key, value);
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal("[Ultimate Sleep] " + err));
            return 0;
        }
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Ultimate Sleep] set " + key + " = " + value), true);
        return 1;
    }
}
