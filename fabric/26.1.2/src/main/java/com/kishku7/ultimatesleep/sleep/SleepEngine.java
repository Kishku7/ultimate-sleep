package com.kishku7.ultimatesleep.sleep;

import com.kishku7.ultimatesleep.config.Settings;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SIMPLE-mode night-skip engine + sleeper messaging.
 *
 * SIMPLE mode is, by design, vanilla's playersSleepingPercentage behavior (Dave: "effectively
 * /gamerule playersSleepingPercentage X"). So we DRIVE that gamerule from
 * required_sleep_percentage and let vanilla perform the actual skip (wake + weather + the 26.x
 * clock advance). Our value-add is the richer per-player broadcast.
 *
 * IMPLEMENTED: gamerule application (applyConfig) + "X is sleeping, S of R required, M more"
 * broadcasts on each new sleeper.
 *
 * TODO (next): AFK-excluded requirement (dynamic gamerule from non-AFK eligible count),
 * ACCELERATE skip mode, preserve_weather, VOTE mode, rewards on wake, world progression,
 * auto-sleep. See FUNCTIONAL_SPEC.md roadmap.
 */
public final class SleepEngine {

    private final Settings settings;
    private final Set<UUID> sleeping = new HashSet<>();

    public SleepEngine(Settings settings) {
        this.settings = settings;
    }

    /** Apply mode-dependent vanilla config. Call on server start and after relevant settings change. */
    public void applyConfig(MinecraftServer server) {
        if (server == null) return;
        if ("SIMPLE".equals(settings.string("requirement_mode"))) {
            int pct = clamp(settings.integer("required_sleep_percentage"), 0, 100);
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(), "gamerule playersSleepingPercentage " + pct);
        }
    }

    /** Per server tick: detect new sleepers and broadcast progress. */
    public void tick(MinecraftServer server) {
        if (!settings.bool("enabled")) return;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        int active = 0;
        Set<UUID> current = new HashSet<>();
        java.util.List<ServerPlayer> nowSleeping = new java.util.ArrayList<>();
        for (ServerPlayer p : players) {
            if (p.isSpectator()) continue;
            active++;
            if (p.isSleeping()) {
                nowSleeping.add(p);
                current.add(p.getUUID());
            }
        }

        if (settings.bool("show_sleepers_in_chat") && !nowSleeping.isEmpty()) {
            int pct = clamp(settings.integer("required_sleep_percentage"), 0, 100);
            int required = Math.max(1, (int) Math.ceil(active * pct / 100.0));
            int s = nowSleeping.size();
            int more = Math.max(0, required - s);
            for (ServerPlayer p : nowSleeping) {
                if (!sleeping.contains(p.getUUID())) {
                    String msg = p.getName().getString() + " is sleeping, " + s + " of " + required
                            + " players required to sleep, " + more + " more required.";
                    server.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
                }
            }
        }

        sleeping.clear();
        sleeping.addAll(current);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
