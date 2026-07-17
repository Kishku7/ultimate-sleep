package com.kishku7.ultimatesleep.sleep;

import com.kishku7.ultimatesleep.compat.Era;

import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.config.Settings;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * VOTE-mode sleep voting.
 *
 * Core principle: being in bed IS a YES. A sleeping player (incl. auto-sleep and a Travelers'
 * Backpack sleeping bag) is recorded as a YES automatically -- no command, no client mod. The vote
 * only exists to ask the players who are still AWAKE whether to skip without them.
 *
 * The prompt is NON-BLOCKING: it is shown on the action bar (the line above the hotbar), pushed by
 * the server, so it never grabs the cursor or freezes the game -- a player mid-fight can ignore it
 * and vote with /usleep yes|no when it's safe. This works identically for vanilla (no-mod) clients.
 *
 *  - First sleeper, everyone eligible already in bed -> skip immediately, no vote.
 *  - Otherwise a vote opens; sleepers are auto-YES; getting into bed mid-vote is an auto-YES; the
 *    vote finishes EARLY once everyone eligible has decided, else at the window's end.
 *  - On PASS: ask the engine to skip (the mod owns every skip via the sleep mixin).
 *  - On FAIL: the players who were in bed (the ones who wanted to sleep) are woken and LOCKED OUT
 *    of sleep for the rest of the night, so they can't instantly restart the vote. Everyone else is
 *    untouched and can still start a fresh vote. Locks clear at daybreak.
 */
public final class VoteManager {

    private final Settings settings;
    private boolean active = false;
    private long startTick = 0;
    private final Map<UUID, Boolean> votes = new HashMap<>();
    private final Set<UUID> lockedTonight = new HashSet<>();

    public VoteManager(Settings settings) {
        this.settings = settings;
    }

    public boolean isLocked(UUID player) {
        return lockedTonight.contains(player);
    }

    public void tick(MinecraftServer server) {
        long now = server.getTickCount();

        // A new day clears all failed-vote lockouts.
        ServerLevel ow = server.overworld();
        if (!lockedTonight.isEmpty() && (ow == null || Era.bright(ow))) {
            lockedTonight.clear();
        }

        if (!"VOTE".equals(settings.string("requirement_mode"))) {
            if (active) { active = false; votes.clear(); }
            return;
        }

        // A pass may be time-lapsing the night (ACCELERATE); don't open a new vote meanwhile.
        if (UltimateSleep.engine().isAccelerating()) return;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        List<ServerPlayer> sleepers = new ArrayList<>();
        for (ServerPlayer p : players) {
            if (p.isSpectator() || !p.isSleeping()) continue;
            if (lockedTonight.contains(p.getUUID())) {
                // Locked this night after losing a vote: keep them out of bed, don't count them.
                p.stopSleepInBed(false, true);
                p.sendSystemMessage(Component.literal(
                        "[Ultimate Sleep] You're locked out of sleep until morning -- your sleep vote failed."));
                continue;
            }
            sleepers.add(p);
        }

        if (!active) {
            if (sleepers.isEmpty()) return;

            // Unanimous shortcut: if no eligible player is still awake, just skip -- nobody to ask.
            if (!anyEligibleAwake(players)) {
                broadcast(server, "Everyone's asleep -- skipping the night.");
                UltimateSleep.engine().performSkip(server);
                return;
            }

            active = true;
            startTick = now;
            votes.clear();
            for (ServerPlayer s : sleepers) votes.put(s.getUUID(), true); // auto-yes the starter(s)
            broadcast(server, "A sleep vote has started -- /usleep yes or /usleep no ("
                    + settings.integer("vote_duration_seconds") + "s).");
            sendPrompt(players, now);
            return;
        }

        // Active: getting into bed mid-vote is an auto-yes.
        for (ServerPlayer s : sleepers) votes.putIfAbsent(s.getUUID(), true);

        // Refresh the non-blocking action-bar prompt about twice a second.
        if ((now - startTick) % 10 == 0) {
            sendPrompt(players, now);
        }

        // Finish early once everyone eligible has decided (in bed or voted), else at the window end.
        boolean everyoneDecided = !anyEligibleUndecided(players);
        if (everyoneDecided || now - startTick >= (long) settings.integer("vote_duration_seconds") * 20L) {
            finishVote(server, players);
        }
    }

    /** Push the vote prompt to the action bar of every awake, eligible, still-undecided player. */
    private void sendPrompt(List<ServerPlayer> players, long now) {
        int secsLeft = (int) Math.max(0,
                (settings.integer("vote_duration_seconds") * 20L - (now - startTick) + 19) / 20);
        String text = "Sleep vote (" + secsLeft + "s): /usleep yes  or  /usleep no";
        // Optional live tally + who's currently in bed, appended to the action-bar prompt. The old
        // blocking vote screen was removed (votes are non-blocking action-bar), so this setting now
        // enriches the prompt itself rather than a popup.
        if (settings.bool("show_sleepers_on_vote_screen")) {
            int yes = 0, no = 0;
            for (boolean v : votes.values()) { if (v) yes++; else no++; }
            StringBuilder beds = new StringBuilder();
            for (ServerPlayer p : players) {
                if (p.isSleeping() && !p.isSpectator()) {
                    if (beds.length() > 0) beds.append(", ");
                    beds.append(p.getName().getString());
                }
            }
            text += "  [Yes " + yes + " / No " + no
                    + (beds.length() > 0 ? "; in bed: " + beds : "") + "]";
        }
        Component msg = Component.literal(text);
        for (ServerPlayer p : players) {
            if (p.isSpectator() || p.isSleeping()) continue;
            if (UltimateSleep.afk().isAfk(p.getUUID())) continue;
            if (lockedTonight.contains(p.getUUID())) continue;
            if (votes.containsKey(p.getUUID())) continue; // already voted
            Era.overlay(p, msg); // action bar (non-blocking)
        }
    }

    /** Any non-spectator, non-AFK, non-locked player who is currently awake (still has a say). */
    private boolean anyEligibleAwake(List<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            if (p.isSpectator()) continue;
            if (UltimateSleep.afk().isAfk(p.getUUID())) continue;
            if (lockedTonight.contains(p.getUUID())) continue;
            if (!p.isSleeping()) return true;
        }
        return false;
    }

    /** Any non-spectator, non-AFK, non-locked player who has neither voted nor gone to bed. */
    private boolean anyEligibleUndecided(List<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            if (p.isSpectator()) continue;
            if (UltimateSleep.afk().isAfk(p.getUUID())) continue;
            if (lockedTonight.contains(p.getUUID())) continue;
            if (!votes.containsKey(p.getUUID())) return true;
        }
        return false;
    }

    public void castVote(ServerPlayer p, boolean yes) {
        if (!"VOTE".equals(settings.string("requirement_mode"))) {
            p.sendSystemMessage(Component.literal("[Ultimate Sleep] Vote mode is not enabled."));
            return;
        }
        if (lockedTonight.contains(p.getUUID())) {
            p.sendSystemMessage(Component.literal("[Ultimate Sleep] You're locked out of sleep votes until morning."));
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
            if (!p.isSpectator() && !UltimateSleep.afk().isAfk(p.getUUID()) && !lockedTonight.contains(p.getUUID())) {
                nonAfk++;
            }
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

        if (pass) {
            broadcast(server, "Sleep vote passed -- skipping the night.");
            UltimateSleep.engine().performSkip(server);
        } else {
            broadcast(server, "Sleep vote failed -- the night continues.");
            // Lock out (and wake) the players who were in bed: they had their say and lost, so they
            // can't restart the vote tonight. Everyone else can still start a fresh vote.
            for (ServerPlayer p : players) {
                if (p.isSleeping() && !p.isSpectator()) {
                    lockedTonight.add(p.getUUID());
                    p.stopSleepInBed(false, true);
                    p.sendSystemMessage(Component.literal(
                            "[Ultimate Sleep] You're locked out of sleep until morning."));
                }
            }
        }
        active = false;
        votes.clear();
    }

    private void broadcast(MinecraftServer server, String m) {
        server.getPlayerList().broadcastSystemMessage(Component.literal("[Ultimate Sleep] " + m), false);
    }
}
