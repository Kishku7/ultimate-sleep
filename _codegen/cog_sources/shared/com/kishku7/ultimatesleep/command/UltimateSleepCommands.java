package com.kishku7.ultimatesleep.command;

import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.config.Settings;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The /usleep command tree. Open: status, query, afk, auto, yes, no.
 * Tiered: /usleep set (op2/sleep-admin), /usleep admin ... (op3/sleep-admin).
 * Setting keys (and values for bool/enum) tab-complete.
 */
public final class UltimateSleepCommands {

    private UltimateSleepCommands() {}

    /** Tab-complete setting keys. */
    private static final SuggestionProvider<CommandSourceStack> KEY_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(UltimateSleep.settings().all().keySet(), builder);

    /** Tab-complete values for the key already typed (bool/enum only). */
    private static final SuggestionProvider<CommandSourceStack> VALUE_SUGGESTIONS = (ctx, builder) -> {
        try {
            Settings.Entry e = UltimateSleep.settings().get(StringArgumentType.getString(ctx, "key"));
            if (e != null && e.type == Settings.Type.BOOL) {
                return SharedSuggestionProvider.suggest(List.of("true", "false"), builder);
            }
            if (e != null && e.type == Settings.Type.ENUM) {
                return SharedSuggestionProvider.suggest(e.allowed, builder);
            }
        } catch (Exception ignored) {
            // key not parsed yet -> no value suggestions
        }
        return builder.buildFuture();
    };

    public static LiteralCommandNode<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register(Commands.literal("usleep")
                .then(Commands.literal("status").executes(UltimateSleepCommands::status))
                .then(Commands.literal("query").executes(UltimateSleepCommands::query)) // all users
                .then(Commands.literal("afk").executes(UltimateSleepCommands::toggleAfk))
                .then(Commands.literal("gui").executes(UltimateSleepCommands::openGui))
                .then(Commands.literal("auto").executes(UltimateSleepCommands::toggleAuto))
                .then(Commands.literal("yes").executes(ctx -> vote(ctx, true)))
                .then(Commands.literal("no").executes(ctx -> vote(ctx, false)))
                .then(Commands.literal("set")
                        .requires(src -> UltimateSleep.permissions().canSet(src))
                        .then(Commands.argument("key", StringArgumentType.word()).suggests(KEY_SUGGESTIONS)
                                .then(Commands.argument("value", StringArgumentType.greedyString()).suggests(VALUE_SUGGESTIONS)
                                        .executes(UltimateSleepCommands::doSet))))
                .then(Commands.literal("admin")
                        .requires(src -> UltimateSleep.permissions().canAdmin(src))
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.word()).suggests(KEY_SUGGESTIONS)
                                        .then(Commands.argument("value", StringArgumentType.greedyString()).suggests(VALUE_SUGGESTIONS)
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

    private static String shown(Settings.Entry e) {
        Object v = e.get();
        return e.type == Settings.Type.BOOL ? String.valueOf(v).toUpperCase() : String.valueOf(v);
    }

        private static int openGui(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            src.sendSystemMessage(Component.literal("Only players can open the GUI."));
            return 0;
        }
        if (!com.kishku7.ultimatesleep.net.UltimateSleepNet.openGuiFor(p)) {
            p.sendSystemMessage(Component.literal("[Ultimate Sleep] The in-game panel needs the Ultimate Sleep client mod. Use /usleep query and /usleep set instead."));
            return 0;
        }
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int afkCount = src.getServer() == null ? 0 : UltimateSleep.afk().afkCount(src.getServer());
        src.sendSystemMessage(Component.literal(
                "[Ultimate Sleep] mode=" + UltimateSleep.settings().string("requirement_mode")
                        + " required=" + UltimateSleep.settings().integer("required_sleep_percentage") + "%"
                        + " afk=" + afkCount));
        return 1;
    }

    /** Compact settings dump (key = value), no wrapping. Available to any user. */
    private static int query(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSystemMessage(Component.literal("[Ultimate Sleep] settings:"));
        for (Settings.Entry e : UltimateSleep.settings().all().values()) {
            src.sendSystemMessage(Component.literal(e.key + " = " + shown(e)));
        }
        src.sendSystemMessage(Component.literal("afk_owner = " + UltimateSleep.afkCommands().ownerLabel()));
        return 1;
    }

    private static int toggleAfk(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            src.sendSystemMessage(Component.literal("Only players can toggle AFK."));
            return 0;
        }
        UltimateSleep.afk().toggleManual(p); // AfkManager sends the confirmation
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
        src.sendSystemMessage(Component.literal("[Ultimate Sleep] Auto-sleep is now " + (on ? "ON" : "OFF") + " for you."));
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
        src.sendSystemMessage(Component.literal("[Ultimate Sleep] " + key + " is set to " + shown(UltimateSleep.settings().get(key))));
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
        UltimateSleep.afk().setManual(target, true);
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
