package com.kishku7.ultimatesleep.sleep;

import com.kishku7.ultimatesleep.UltimateSleepPlugin;
import com.kishku7.ultimatesleep.config.PluginSettings;
import com.kishku7.ultimatesleep.platform.Platform;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * VOTE-mode sleep voting (Bukkit port of the mod's VoteManager; behavior is 1:1).
 *
 * Core principle: being in bed IS a YES. The vote only exists to ask the players who are still
 * AWAKE whether to skip without them.
 *
 * The prompt is NON-BLOCKING: it is shown on the action bar (via the Spigot-compatible bungee
 * chat path so one jar serves Spigot, Paper, and Folia), refreshed about twice a second; players
 * vote with /usleep yes|no.
 *
 *  - First sleeper, everyone eligible already in bed -> skip immediately, no vote.
 *  - Otherwise a vote opens; sleepers are auto-YES; getting into bed mid-vote is an auto-YES; the
 *    vote finishes EARLY once everyone eligible has decided, else at the window's end.
 *  - On PASS: ask the engine to skip.
 *  - On FAIL: the players who were in bed are woken and LOCKED OUT of sleep for the rest of the
 *    night. Everyone else can still start a fresh vote. Locks clear at daybreak.
 */
public final class VoteManager {

    private final UltimateSleepPlugin plugin;
    private final PluginSettings settings;
    private boolean active = false;
    private long startTick = 0;
    private final Map<UUID, Boolean> votes = new HashMap<>();
    private final Set<UUID> lockedTonight = new HashSet<>();

    public VoteManager(UltimateSleepPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public boolean isLocked(UUID player) {
        return lockedTonight.contains(player);
    }

    public boolean isActive() {
        return active;
    }

    public void tick(long now) {
        // A new day clears all failed-vote lockouts.
        World w = plugin.primaryWorld();
        if (!lockedTonight.isEmpty() && (w == null || SleepEngine.bright(w))) {
            lockedTonight.clear();
        }

        if (!settings.bool("enabled") || !"VOTE".equals(settings.string("requirement_mode"))) {
            if (active) { active = false; votes.clear(); }
            return;
        }

        // A pass may be time-lapsing the night (ACCELERATE); don't open a new vote meanwhile.
        if (plugin.engine().isAccelerating()) return;

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        List<Player> sleepers = new ArrayList<>();
        for (Player p : players) {
            if (p.getGameMode() == GameMode.SPECTATOR || !p.isSleeping()) continue;
            if (lockedTonight.contains(p.getUniqueId())) {
                // Locked this night after losing a vote: keep them out of bed, don't count them.
                Platform.runOnEntity(plugin, p, () -> {
                    if (p.isSleeping()) p.wakeup(false);
                });
                p.sendMessage("[Ultimate Sleep] You're locked out of sleep until morning -- your sleep vote failed.");
                continue;
            }
            sleepers.add(p);
        }

        if (!active) {
            if (sleepers.isEmpty()) return;

            // Unanimous shortcut: if no eligible player is still awake, just skip -- nobody to ask.
            if (!anyEligibleAwake(players)) {
                plugin.broadcast("Everyone's asleep -- skipping the night.");
                plugin.engine().performSkip();
                return;
            }

            active = true;
            startTick = now;
            votes.clear();
            for (Player s : sleepers) votes.put(s.getUniqueId(), true); // auto-yes the starter(s)
            plugin.broadcast("A sleep vote has started -- /usleep yes or /usleep no ("
                    + settings.integer("vote_duration_seconds") + "s).");
            sendPrompt(players, now);
            return;
        }

        // Active: getting into bed mid-vote is an auto-yes.
        for (Player s : sleepers) votes.putIfAbsent(s.getUniqueId(), true);

        // Refresh the non-blocking action-bar prompt about twice a second.
        if ((now - startTick) % 10 == 0) {
            sendPrompt(players, now);
        }

        // Finish early once everyone eligible has decided (in bed or voted), else at the window end.
        boolean everyoneDecided = !anyEligibleUndecided(players);
        if (everyoneDecided || now - startTick >= (long) settings.integer("vote_duration_seconds") * 20L) {
            finishVote(players);
        }
    }

    /** Push the vote prompt to the action bar of every awake, eligible, still-undecided player. */
    private void sendPrompt(List<Player> players, long now) {
        int secsLeft = (int) Math.max(0,
                (settings.integer("vote_duration_seconds") * 20L - (now - startTick) + 19) / 20);
        String text = "Sleep vote (" + secsLeft + "s): /usleep yes  or  /usleep no";
        // Optional live tally + who's currently in bed, appended to the action-bar prompt.
        if (settings.bool("show_sleepers_on_vote_screen")) {
            int yes = 0, no = 0;
            for (boolean v : votes.values()) { if (v) yes++; else no++; }
            StringBuilder beds = new StringBuilder();
            for (Player p : players) {
                if (p.isSleeping() && p.getGameMode() != GameMode.SPECTATOR) {
                    if (beds.length() > 0) beds.append(", ");
                    beds.append(p.getName());
                }
            }
            text += "  [Yes " + yes + " / No " + no
                    + (beds.length() > 0 ? "; in bed: " + beds : "") + "]";
        }
        for (Player p : players) {
            if (p.getGameMode() == GameMode.SPECTATOR || p.isSleeping()) continue;
            if (plugin.afk().isAfk(p.getUniqueId())) continue;
            if (lockedTonight.contains(p.getUniqueId())) continue;
            if (votes.containsKey(p.getUniqueId())) continue; // already voted
            actionBar(p, text);
        }
    }

    /**
     * Non-blocking action-bar line. Uses the Spigot bungee-chat path (present on Spigot, Paper,
     * and Folia alike) instead of Paper's Adventure sendActionBar, so the SAME jar loads on plain
     * Spigot, which does not bundle Adventure.
     */
    @SuppressWarnings("deprecation") // Paper deprecates the bungee path; it is the only one Spigot has.
    private static void actionBar(Player p, String text) {
        p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(text));
    }

    /** Any non-spectator, non-AFK, non-locked player who is currently awake (still has a say). */
    private boolean anyEligibleAwake(List<Player> players) {
        for (Player p : players) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (plugin.afk().isAfk(p.getUniqueId())) continue;
            if (lockedTonight.contains(p.getUniqueId())) continue;
            if (!p.isSleeping()) return true;
        }
        return false;
    }

    /** Any non-spectator, non-AFK, non-locked player who has neither voted nor gone to bed. */
    private boolean anyEligibleUndecided(List<Player> players) {
        for (Player p : players) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (plugin.afk().isAfk(p.getUniqueId())) continue;
            if (lockedTonight.contains(p.getUniqueId())) continue;
            if (!votes.containsKey(p.getUniqueId())) return true;
        }
        return false;
    }

    public void castVote(Player p, boolean yes) {
        if (!"VOTE".equals(settings.string("requirement_mode"))) {
            p.sendMessage("[Ultimate Sleep] Vote mode is not enabled.");
            return;
        }
        if (lockedTonight.contains(p.getUniqueId())) {
            p.sendMessage("[Ultimate Sleep] You're locked out of sleep votes until morning.");
            return;
        }
        if (!active) {
            p.sendMessage("[Ultimate Sleep] No sleep vote is active.");
            return;
        }
        if (plugin.afk().isAfk(p.getUniqueId())) {
            p.sendMessage("[Ultimate Sleep] AFK players can't vote.");
            return;
        }
        votes.put(p.getUniqueId(), yes);
        p.sendMessage("[Ultimate Sleep] Vote recorded: " + (yes ? "YES" : "NO") + ".");
    }

    private void finishVote(List<Player> players) {
        int cast = votes.size();
        int yes = 0;
        for (boolean v : votes.values()) if (v) yes++;
        int no = cast - yes;

        int nonAfk = 0;
        for (Player p : players) {
            if (p.getGameMode() != GameMode.SPECTATOR
                    && !plugin.afk().isAfk(p.getUniqueId())
                    && !lockedTonight.contains(p.getUniqueId())) {
                nonAfk++;
            }
        }

        boolean pass = switch (settings.string("vote_pass_rule")) {
            case "PERCENT_CAST" -> cast > 0 && (yes * 100) / cast >= settings.integer("vote_pass_percentage");
            case "MAJORITY_NON_AFK" -> yes * 2 > nonAfk;
            default -> yes > no; // MAJORITY_CAST
        };

        for (Map.Entry<UUID, Boolean> e : votes.entrySet()) {
            Player voter = Bukkit.getPlayer(e.getKey());
            if (voter != null) {
                voter.sendMessage(e.getValue() == pass
                        ? "[Ultimate Sleep] Your sleep vote carried."
                        : "[Ultimate Sleep] You were outvoted.");
            }
        }

        if (pass) {
            plugin.broadcast("Sleep vote passed -- skipping the night.");
            plugin.engine().performSkip();
        } else {
            plugin.broadcast("Sleep vote failed -- the night continues.");
            // Lock out (and wake) the players who were in bed: they had their say and lost, so they
            // can't restart the vote tonight. Everyone else can still start a fresh vote.
            for (Player p : players) {
                if (p.isSleeping() && p.getGameMode() != GameMode.SPECTATOR) {
                    lockedTonight.add(p.getUniqueId());
                    Platform.runOnEntity(plugin, p, () -> {
                        if (p.isSleeping()) p.wakeup(false);
                    });
                    p.sendMessage("[Ultimate Sleep] You're locked out of sleep until morning.");
                }
            }
        }
        active = false;
        votes.clear();
    }
}
