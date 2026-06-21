package com.kishku7.ultimatesleep.sleep;

import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.config.Settings;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * VOTE-mode sleep voting (command path).
 *
 * When requirement_mode == VOTE, the first player to get into bed auto-starts a vote (and
 * auto-votes YES). Everyone non-AFK votes with /usleep yes | /usleep no during the
 * vote_duration_seconds window; getting into bed mid-vote is an auto-YES. After the window the
 * result is tallied per vote_pass_rule. On PASS we skip the night by briefly setting
 * playersSleepingPercentage to 0 (vanilla then skips, since the original sleeper is deep-asleep)
 * and restore it right after. On FAIL the night continues.
 *
 * The client vote popup is deferred to the GUI phase; this is the full command-path implementation.
 */
public final class VoteManager {

    private final Settings settings;
    private boolean active = false;
    private long startTick = 0;
    private long restoreGameruleAtTick = -1;
    private final Map<UUID, Boolean> votes = new HashMap<>();

    public VoteManager(Settings settings) {
        this.settings = settings;
    }

    public void tick(MinecraftServer server) {
        long now = server.getTickCount();

        // Restore the gamerule shortly after a pass-skip.
        if (restoreGameruleAtTick >= 0 && now >= restoreGameruleAtTick) {
            setGamerule(server, 100);
            restoreGameruleAtTick = -1;
        }

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
            if (!sleepers.isEmpty()) {
                active = true;
                startTick = now;
                votes.clear();
                for (ServerPlayer s : sleepers) votes.put(s.getUUID(), true); // auto-yes the starter(s)
                broadcast(server, "A sleep vote has started! Use /usleep yes or /usleep no ("
                        + settings.integer("vote_duration_seconds") + "s).");
            }
            return;
        }

        // Active: getting into bed mid-vote is an auto-yes.
        for (ServerPlayer s : sleepers) votes.putIfAbsent(s.getUUID(), true);

        if (now - startTick >= (long) settings.integer("vote_duration_seconds") * 20L) {
            finishVote(server, players);
        }
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

        if (pass) {
            broadcast(server, "Sleep vote PASSED (" + yes + " yes / " + no + " no) -- skipping the night.");
            setGamerule(server, 0);
            restoreGameruleAtTick = server.getTickCount() + 10;
        } else {
            broadcast(server, "Sleep vote FAILED (" + yes + " yes / " + no + " no) -- the night continues.");
        }
        active = false;
        votes.clear();
    }

    private void setGamerule(MinecraftServer server, int v) {
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(), "gamerule playersSleepingPercentage " + v);
    }

    private void broadcast(MinecraftServer server, String m) {
        server.getPlayerList().broadcastSystemMessage(Component.literal("[Ultimate Sleep] " + m), false);
    }
}
