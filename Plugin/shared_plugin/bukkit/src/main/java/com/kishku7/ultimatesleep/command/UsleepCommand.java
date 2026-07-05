package com.kishku7.ultimatesleep.command;

import com.kishku7.ultimatesleep.UltimateSleepPlugin;
import com.kishku7.ultimatesleep.config.PluginSettings;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The /usleep command tree + the /afk alias (Bukkit port of the mod's command surface,
 * FUNCTIONAL_SPEC.md section 2).
 *
 * Player commands (open): status, query, afk, auto, yes, no.
 * Config commands (tiered): set <key> <value> (ultimatesleep.set node / sleep-admin / console),
 * admin set <key> <value>, admin afk <player>, admin admins add|remove|list [player]
 * (ultimatesleep.admin node / sleep-admin / console).
 *
 * /afk is declared in plugin.yml: Bukkit's native collision handling IS the mod's "stand down"
 * behavior -- if another plugin also registers /afk, the first registrant keeps the bare name and
 * ours stays reachable as /ultimatesleep:afk; provide_afk_command=false additionally turns the
 * alias into a pointer at /usleep afk.
 */
public final class UsleepCommand implements TabExecutor {

    private static final String PREFIX = "[Ultimate Sleep] ";
    private static final List<String> SUBS =
            List.of("status", "query", "afk", "auto", "yes", "no", "set", "admin");

    private final UltimateSleepPlugin plugin;

    public UsleepCommand(UltimateSleepPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("afk")) {
            return handleAfkAlias(sender);
        }
        if (args.length == 0) {
            return status(sender);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "status" -> status(sender);
            case "query" -> query(sender);
            case "afk" -> playerOnly(sender, p ->
                    plugin.afk().toggleManual(p, plugin.tickCount()));
            case "auto" -> playerOnly(sender, p -> {
                boolean on = plugin.autoSleep().toggle(p);
                p.sendMessage(PREFIX + "Auto-sleep " + (on ? "enabled" : "disabled") + " for you.");
            });
            case "yes" -> playerOnly(sender, p -> plugin.votes().castVote(p, true));
            case "no" -> playerOnly(sender, p -> plugin.votes().castVote(p, false));
            case "set" -> handleSet(sender, args, 1, plugin.permissions().canSet(sender));
            case "admin" -> handleAdmin(sender, args);
            default -> {
                sender.sendMessage(PREFIX + "Unknown subcommand. Try: " + String.join(", ", SUBS));
                yield true;
            }
        };
    }

    private boolean handleAfkAlias(CommandSender sender) {
        if (!plugin.settings().bool("provide_afk_command")) {
            sender.sendMessage(PREFIX + "The /afk alias is disabled here -- use /usleep afk.");
            return true;
        }
        return playerOnly(sender, p -> plugin.afk().toggleManual(p, plugin.tickCount()));
    }

    private interface PlayerAction { void run(Player p); }

    private boolean playerOnly(CommandSender sender, PlayerAction action) {
        if (sender instanceof Player p) {
            action.run(p);
        } else {
            sender.sendMessage(PREFIX + "That subcommand is for players.");
        }
        return true;
    }

    private boolean status(CommandSender sender) {
        PluginSettings s = plugin.settings();
        StringBuilder line = new StringBuilder(PREFIX);
        if (!s.bool("enabled")) {
            line.append("DISABLED (enabled=false). ");
        }
        line.append("mode=").append(s.string("requirement_mode"));
        if ("SIMPLE".equals(s.string("requirement_mode"))) {
            line.append(" (").append(s.integer("required_sleep_percentage")).append("%)");
        }
        line.append(", skip=").append(s.string("skip_mode"));
        line.append(", AFK=").append(plugin.afk().afkCount());
        line.append(", vote=").append(plugin.votes().isActive() ? "ACTIVE" : "none");
        if (plugin.engine().isAccelerating()) line.append(", night is time-lapsing");
        sender.sendMessage(line.toString());
        return true;
    }

    private boolean query(CommandSender sender) {
        sender.sendMessage(PREFIX + "Settings:");
        for (PluginSettings.Entry e : plugin.settings().all().values()) {
            sender.sendMessage("  " + e.key + " = " + e.asString());
        }
        List<String> admins = plugin.permissions().list();
        sender.sendMessage("  sleep-admins: " + (admins.isEmpty() ? "(none)" : String.join(", ", admins)));
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args, int from, boolean allowed) {
        if (!allowed) {
            sender.sendMessage(PREFIX + "You don't have permission for that.");
            return true;
        }
        if (args.length < from + 2) {
            sender.sendMessage(PREFIX + "Usage: /usleep " + (from == 1 ? "" : "admin ") + "set <key> <value>");
            return true;
        }
        String key = args[from].toLowerCase(Locale.ROOT);
        String value = args[from + 1];
        String err = plugin.settings().setRaw(key, value);
        if (err != null) {
            sender.sendMessage(PREFIX + err);
        } else {
            sender.sendMessage(PREFIX + key + " = " + plugin.settings().get(key).asString());
        }
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!plugin.permissions().canAdmin(sender)) {
            sender.sendMessage(PREFIX + "You don't have permission for that.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "Usage: /usleep admin set|afk|admins ...");
            return true;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "set" -> handleSet(sender, args, 2, true);
            case "afk" -> {
                if (args.length < 3) {
                    sender.sendMessage(PREFIX + "Usage: /usleep admin afk <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(PREFIX + "Player not found: " + args[2]);
                    return true;
                }
                plugin.afk().setManual(target, true, plugin.tickCount());
                sender.sendMessage(PREFIX + target.getName() + " is now AFK.");
            }
            case "admins" -> handleAdmins(sender, args);
            default -> sender.sendMessage(PREFIX + "Usage: /usleep admin set|afk|admins ...");
        }
        return true;
    }

    private void handleAdmins(CommandSender sender, String[] args) {
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage(PREFIX + "Usage: /usleep admin admins add <player>");
                    return;
                }
                sender.sendMessage(PREFIX + (plugin.permissions().addAdmin(args[3])
                        ? args[3] + " is now a sleep-admin."
                        : args[3] + " is already a sleep-admin."));
            }
            case "remove" -> {
                if (args.length < 4) {
                    sender.sendMessage(PREFIX + "Usage: /usleep admin admins remove <player>");
                    return;
                }
                sender.sendMessage(PREFIX + (plugin.permissions().removeAdmin(args[3])
                        ? args[3] + " is no longer a sleep-admin."
                        : args[3] + " was not a sleep-admin."));
            }
            case "list" -> {
                List<String> admins = plugin.permissions().list();
                sender.sendMessage(PREFIX + "Sleep-admins: "
                        + (admins.isEmpty() ? "(none)" : String.join(", ", admins)));
            }
            default -> sender.sendMessage(PREFIX + "Usage: /usleep admin admins add|remove|list");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("afk")) return List.of();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            addMatches(out, SUBS, args[0]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            addMatches(out, plugin.settings().all().keySet(), args[1]);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            addMatches(out, allowedValues(args[1]), args[2]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            addMatches(out, List.of("set", "afk", "admins"), args[1]);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("set")) {
            addMatches(out, plugin.settings().all().keySet(), args[2]);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("set")) {
            addMatches(out, allowedValues(args[2]), args[3]);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("afk")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("admins")) {
            addMatches(out, List.of("add", "remove", "list"), args[2]);
        }
        return out;
    }

    private List<String> allowedValues(String key) {
        PluginSettings.Entry e = plugin.settings().get(key.toLowerCase(Locale.ROOT));
        if (e == null) return List.of();
        if (e.type == PluginSettings.Type.ENUM) return e.allowed;
        if (e.type == PluginSettings.Type.BOOL) return List.of("true", "false");
        return List.of();
    }

    private static void addMatches(List<String> out, Iterable<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        }
    }
}
