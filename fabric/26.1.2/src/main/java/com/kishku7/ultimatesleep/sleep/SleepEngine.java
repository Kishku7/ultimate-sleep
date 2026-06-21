package com.kishku7.ultimatesleep.sleep;

import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.config.Settings;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SIMPLE-mode night-skip engine + sleeper messaging, with optional AFK exclusion.
 *
 * SIMPLE mode mirrors vanilla playersSleepingPercentage. Two paths:
 *  - exclude_afk_from_requirement = false: we set the gamerule to required_sleep_percentage and
 *    let vanilla do the skip; we just broadcast progress.
 *  - exclude_afk_from_requirement = true: vanilla can't exclude AFK players, so we set the
 *    gamerule to 100 (vanilla won't auto-skip) and drive the skip ourselves: when the sleeping
 *    NON-AFK count reaches the required fraction of NON-AFK eligible players (and at least one is
 *    deep-asleep, matching vanilla's gate), we briefly set the gamerule to 0 so vanilla performs
 *    the actual skip (wake + weather + 26.x clock advance), then restore it.
 *
 * VOTE mode is handled by VoteManager (gamerule pinned to 100).
 *
 * TODO (next): ACCELERATE skip, preserve_weather, world progression. See FUNCTIONAL_SPEC.md.
 */
public final class SleepEngine {

    private final Settings settings;
    private final Set<UUID> sleeping = new HashSet<>();
    private long restoreGameruleAtTick = -1;

    public SleepEngine(Settings settings) {
        this.settings = settings;
    }

    /** Apply mode-dependent vanilla config. Call on server start and after relevant settings change. */
    public void applyConfig(MinecraftServer server) {
        if (server == null) return;
        int pct;
        if ("SIMPLE".equals(settings.string("requirement_mode"))) {
            pct = settings.bool("exclude_afk_from_requirement")
                    ? 100 // we drive the skip ourselves to exclude AFK
                    : clamp(settings.integer("required_sleep_percentage"), 0, 100);
        } else {
            pct = 100; // VOTE: the vote controls skipping
        }
        setGamerule(server, pct);
    }

    public void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (restoreGameruleAtTick >= 0 && now >= restoreGameruleAtTick) {
            setGamerule(server, 100);
            restoreGameruleAtTick = -1;
        }

        if (!settings.bool("enabled")) return;
        if (!"SIMPLE".equals(settings.string("requirement_mode"))) {
            if (!sleeping.isEmpty()) sleeping.clear();
            return;
        }

        boolean excludeAfk = settings.bool("exclude_afk_from_requirement");
        int pct = clamp(settings.integer("required_sleep_percentage"), 0, 100);

        int eligible = 0, sleepCount = 0, deep = 0;
        Set<UUID> current = new HashSet<>();
        List<ServerPlayer> newlySleeping = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) continue;
            if (excludeAfk && UltimateSleep.afk().isAfk(p.getUUID())) continue;
            eligible++;
            if (p.isSleeping()) {
                sleepCount++;
                current.add(p.getUUID());
                if (p.isSleepingLongEnough()) deep++;
                if (!sleeping.contains(p.getUUID())) newlySleeping.add(p);
            }
        }

        int required = Math.max(1, (int) Math.ceil(eligible * pct / 100.0));

        if (settings.bool("show_sleepers_in_chat") && !newlySleeping.isEmpty()) {
            int more = Math.max(0, required - sleepCount);
            for (ServerPlayer p : newlySleeping) {
                server.getPlayerList().broadcastSystemMessage(Component.literal(
                        p.getName().getString() + " is sleeping, " + sleepCount + " of " + required
                                + " players required to sleep, " + more + " more required."), false);
            }
        }

        // AFK-excluded path: we trigger the skip ourselves once the non-AFK requirement is met.
        if (excludeAfk && sleepCount >= required && deep >= 1) {
            setGamerule(server, 0);
            restoreGameruleAtTick = now + 10;
        }

        sleeping.clear();
        sleeping.addAll(current);
    }

    private void setGamerule(MinecraftServer server, int v) {
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(), "gamerule playersSleepingPercentage " + v);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
