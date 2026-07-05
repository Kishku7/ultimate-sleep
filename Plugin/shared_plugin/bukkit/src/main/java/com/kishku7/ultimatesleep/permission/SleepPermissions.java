package com.kishku7.ultimatesleep.permission;

import com.kishku7.ultimatesleep.UltimateSleepPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Command permission tiers + the sleep-admin roster (Bukkit port of the mod's SleepPermissions).
 *
 * Tiers (FUNCTIONAL_SPEC.md section 2, mapped onto Bukkit permissions):
 *   - player commands (status/query/afk/auto/yes/no): open to all.
 *   - /usleep set:   the "ultimatesleep.set" node (default op) OR a designated sleep-admin.
 *   - /usleep admin: the "ultimatesleep.admin" node (default op) OR a designated sleep-admin.
 *   - A designated sleep-admin needs NO op/permission and satisfies BOTH tiers.
 *   - Console always passes both tiers.
 *
 * Roster is persisted as a list of lower-cased player names under sleep_admins in config.yml
 * (mirrors the mod's ultimate_sleep_admins.json).
 */
public final class SleepPermissions {

    public static final String NODE_SET = "ultimatesleep.set";
    public static final String NODE_ADMIN = "ultimatesleep.admin";
    private static final String CFG_KEY = "sleep_admins";

    private final UltimateSleepPlugin plugin;
    private final Set<String> sleepAdmins = new LinkedHashSet<>();

    public SleepPermissions(UltimateSleepPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isSleepAdmin(CommandSender sender) {
        return sender instanceof Player p
                && sleepAdmins.contains(p.getName().toLowerCase(Locale.ROOT));
    }

    /** /usleep set tier: console, the set node, or sleep-admin. */
    public boolean canSet(CommandSender sender) {
        if (!(sender instanceof Player)) return true;
        return isSleepAdmin(sender) || sender.hasPermission(NODE_SET);
    }

    /** /usleep admin tier: console, the admin node, or sleep-admin. */
    public boolean canAdmin(CommandSender sender) {
        if (!(sender instanceof Player)) return true;
        return isSleepAdmin(sender) || sender.hasPermission(NODE_ADMIN);
    }

    public boolean addAdmin(String name) {
        boolean added = sleepAdmins.add(name.toLowerCase(Locale.ROOT));
        if (added) save();
        return added;
    }

    public boolean removeAdmin(String name) {
        boolean removed = sleepAdmins.remove(name.toLowerCase(Locale.ROOT));
        if (removed) save();
        return removed;
    }

    public List<String> list() {
        return new ArrayList<>(sleepAdmins);
    }

    public void load() {
        sleepAdmins.clear();
        for (String n : plugin.getConfig().getStringList(CFG_KEY)) {
            sleepAdmins.add(n.toLowerCase(Locale.ROOT));
        }
    }

    private void save() {
        plugin.getConfig().set(CFG_KEY, new ArrayList<>(sleepAdmins));
        plugin.saveConfig();
    }
}
