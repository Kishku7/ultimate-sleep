package com.kishku7.ultimatesleep.permission;

import com.kishku7.ultimatesleep.compat.Era;

import com.kishku7.ultimatesleep.Platform;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Command permission tiers + the sleep-admin roster.
 *
 * Tiers (the functional spec section 2):
 *   - query + player commands: open to all (handled by the commands, not here).
 *   - /usleep set: op level 2 (COMMANDS_GAMEMASTER) OR a designated sleep-admin.
 *   - /usleep admin ...: op level 3 (COMMANDS_ADMIN) OR a designated sleep-admin.
 *   - A designated sleep-admin needs NO op level and is treated as op level 4 for ALL of our
 *     commands (so they satisfy both canSet and canAdmin).
 *
 * Roster is persisted as a JSON list of (lower-cased) player names in the Fabric config dir.
 * TODO: a permission-node hook (fabric-permissions-api / LuckPerms) is roadmap step 2.
 */
public final class SleepPermissions {

    private final Set<String> sleepAdmins = new LinkedHashSet<>();
    private final Path file = Platform.configDir().resolve("ultimate_sleep_admins.json");
    private static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean isSleepAdmin(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        return p != null && sleepAdmins.contains(p.getName().getString().toLowerCase());
    }

    /** /usleep set tier: op 2 or sleep-admin. */
    public boolean canSet(CommandSourceStack src) {
        return isSleepAdmin(src) || Era.hasGamemaster(src);
    }

    /** /usleep admin tier: op 3 or sleep-admin. */
    public boolean canAdmin(CommandSourceStack src) {
        return isSleepAdmin(src) || Era.hasAdmin(src);
    }

    public boolean addAdmin(String name) {
        boolean added = sleepAdmins.add(name.toLowerCase());
        if (added) save();
        return added;
    }

    public boolean removeAdmin(String name) {
        boolean removed = sleepAdmins.remove(name.toLowerCase());
        if (removed) save();
        return removed;
    }

    public List<String> list() {
        return new ArrayList<>(sleepAdmins);
    }

    public void load() {
        try {
            if (Files.exists(file)) {
                List<String> names = GSON.fromJson(Files.readString(file),
                        new TypeToken<List<String>>() {}.getType());
                sleepAdmins.clear();
                if (names != null) for (String n : names) sleepAdmins.add(n.toLowerCase());
            }
        } catch (Exception ex) {
            UltimateSleep.LOGGER.warn("[UltimateSleep] failed to load sleep-admins: " + ex.getMessage());
        }
    }

    public void save() {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(new ArrayList<>(sleepAdmins)));
        } catch (Exception ex) {
            UltimateSleep.LOGGER.warn("[UltimateSleep] failed to save sleep-admins: " + ex.getMessage());
        }
    }
}
