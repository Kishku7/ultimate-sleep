package com.kishku7.ultimatesleep.sleep;

import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.config.Settings;
import com.kishku7.ultimatesleep.net.UltimateSleepNet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * VOTE-mode sleep voting.
 *
 * Core principle: being in bed IS a YES. A player who is sleeping (including auto-sleep and a
 * Travelers' Backpack sleeping bag) is recorded as a YES automatically -- no command, and no client
 * mod required. The vote only exists to ask the players who are still AWAKE whether to skip without
 * them.
 *
 * So:
 *  - When the first player sleeps, if EVERY eligible (non-spectator, non-AFK) player is already in
 *    bed, there is no one left to ask -> skip immediately, no vote UI (the "everyone's asleep"
 *    case, e.g. everyone auto-slept).
 *  - Otherwise a vote opens: sleepers are auto-YES, the awake eligible players get the popup (modded
 *    clients) and/or use /usleep yes|no. Getting into bed mid-vote is an auto-YES.
 *  - The vote finishes EARLY the moment every eligible player has decided (in bed or voted), instead
 *    of always waiting out vote_duration_seconds; otherwise it tallies when the window ends.
 *  - On a pass we ask the engine to skip (gamerule stays pinned at 101; the mod owns the skip).
 */
public final class VoteManager {

    private final Settings settings;
    private boolean active = false;
    private long startTick = 0;
    private final Map<UUID, Boolean> votes = new HashMap<>();

    public VoteManager(Settings settings) {
        this.settings = settings;
    }

    public void tick(MinecraftServer server) {
        long now = server.getTickCount();

        if (!"VOTE".equals(settings.string("requirement_mode"))) {
            if (active) { active = false; votes.clear(); }
            return;
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        List<ServerPlayer> sleepers = new ArrayList<>();
        for (ServerPlayer p : players) {
            if (!p.isSpectator() && p.isSleeping()) sleepers.add(p);
        }

        if (!active) {
            if (sleepers.isEmpty()) return;

            // Unanimous shortcut: if no eligible player is still awake, just skip -- nobody to ask.
            if (!anyEligibleAwake(players)) {
                broadcast(server, "Everyone's asleep -- skipping the night.");
                UltimateSleep.engine().requestSkip();
                return;
            }

            active = true;
            startTick = now;
            votes.clear();
            for (ServerPlayer s : sleepers) votes.put(s.getUUID(), true); // auto-yes the starter(s)
            broadcast(server, "A sleep vote has started! Use /usleep yes or /usleep no ("
                    + settings.integer("vote_duration_seconds") + "s).");
            for (ServerPlayer pl : players) {
                if (!pl.isSpectator() && !UltimateSleep.afk().isAfk(pl.getUUID()) && !votes.containsKey(pl.getUUID())) {
                    UltimateSleepNet.sendVoteStart(pl, "Do you want to allow sleep without you?", settings.integer("vote_duration_seconds"));
                }
            }
            return;
        }

        // Active: getting into bed mid-vote is an auto-yes.
        for (ServerPlayer s : sleepers) votes.putIfAbsent(s.getUUID(), true);

        // Live tally + sleeper list to the open popups, ~once a second.
        if (settings.bool("show_sleepers_on_vote_screen") && (now - startTick) % 20 == 0) {
            int yes = 0;
            for (boolean v : votes.values()) if (v) yes++;
            int no = votes.size() - yes;
            StringBuilder sb = new StringBuilder();
            for (ServerPlayer s : sleepers) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(s.getName().getString());
            }
            String names = sb.toString();
            for (ServerPlayer pl : players) {
                if (!pl.isSpectator() && !UltimateSleep.afk().isAfk(pl.getUUID())) {
                    UltimateSleepNet.sendVoteInfo(pl, yes, no, names);
                }
            }
        }

        // Finish early once everyone eligible has decided (in bed or voted), else at the window end.
        boolean everyoneDecided = !anyEligibleUndecided(players);
        if (everyoneDecided || now - startTick >= (long) settings.integer("vote_duration_seconds") * 20L) {
            finishVote(server, players);
        }
    }

    /** Any non-spectator, non-AFK player who is currently awake (still has a say). */
    private boolean anyEligibleAwake(List<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            if (p.isSpectator()) continue;
            if (UltimateSleep.afk().isAfk(p.getUUID())) continue;
            if (!p.isSleeping()) return true;
        }
        return false;
    }

    /** Any non-spectator, non-AFK player who has neither voted nor gone to bed. */
    private boolean anyEligibleUndecided(List<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            if (p.isSpectator()) continue;
            if (UltimateSleep.afk().isAfk(p.getUUID())) continue;
            if (!votes.containsKey(p.getUUID())) return true;
        }
        return false;
    }

    public void castVote(ServerPlayer p, boolean yes) {
        if (!"VOTE".equals(settings.string("requirement_mode"))) {
            p.sendSystemMessage(Component.literal("[Ultimate Sleep] Vote mode is not enabled."));
            return;
        }
        if (!active) {
            p.sendSystemMessage(Component.literal("[Ultimate Sleep] No sleep vote is active."));
            return;
        }
        if (UltimateSleep.afk().isAfk(p.getUUID())) {
            p.sendSystemMessage(Component.literal("[Ultimate Sleep] AFK players can't vote."));
            return;
        }
        votes.put(p.getUUID(), yes);
        p.sendSystemMessage(Component.literal("[Ultimate Sleep] Vote recorded: " + (yes ? "YES" : "NO") + "."));
    }

    private void finishVote(MinecraftServer server, List<ServerPlayer> players) {
        int cast = votes.size();
        int yes = 0;
        for (boolean v : votes.values()) if (v) yes++;
        int no = cast - yes;

        int nonAfk = 0;
        for (ServerPlayer p : players) {
            if (!p.isSpectator() && !UltimateSleep.afk().isAfk(p.getUUID())) nonAfk++;
        }

        boolean pass = switch (settings.string("vote_pass_rule")) {
            case "PERCENT_CAST" -> cast > 0 && (yes * 100) / cast >= settings.integer("vote_pass_percentage");
            case "MAJORITY_NON_AFK" -> yes * 2 > nonAfk;
            default -> yes > no; // MAJORITY_CAST
        };

        for (Map.Entry<UUID, Boolean> e : votes.entrySet()) {
            ServerPlayer voter = server.getPlayerList().getPlayer(e.getKey());
            if (voter != null) {
                voter.sendSystemMessage(Component.literal(e.getValue() == pass
                        ? "[Ultimate Sleep] Your sleep vote carried."
                        : "[Ultimate Sleep] You were outvoted."));
            }
        }
        for (ServerPlayer pl : players) {
            UltimateSleepNet.sendVoteEnd(pl);
        }

        if (pass) {
            broadcast(server, "Sleep vote passed -- skipping the night.");
            UltimateSleep.engine().requestSkip();
        } else {
            broadcast(server, "Sleep vote failed -- the night continues.");
        }
        active = false;
        votes.clear();
    }

    private void broadcast(MinecraftServer server, String m) {
        server.getPlayerList().broadcastSystemMessage(Component.literal("[Ultimate Sleep] " + m), false);
    }
}
