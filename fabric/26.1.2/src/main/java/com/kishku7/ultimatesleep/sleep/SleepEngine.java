package com.kishku7.ultimatesleep.sleep;

import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.config.Settings;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SIMPLE-mode night-skip engine + sleeper messaging, with AFK exclusion and two skip modes.
 *
 * The vanilla playersSleepingPercentage gamerule is kept honest = required_sleep_percentage in
 * INSTANT mode (so /gamerule matches the mod and the messages). When exclude_afk_from_requirement
 * is on we additionally drive the skip over the NON-AFK eligible count (briefly bursting the
 * gamerule to 0 so vanilla performs the skip the moment that count is met -- this only fires
 * EARLIER than vanilla, never later). ACCELERATE pins the gamerule to 100 and time-lapses instead.
 * VOTE mode (VoteManager) pins it to 100.
 */
public final class SleepEngine {

    private final Settings settings;
    private final Set<UUID> sleeping = new HashSet<>();
    private long restoreGameruleAtTick = -1;
    private volatile boolean accelerating = false;

    public SleepEngine(Settings settings) {
        this.settings = settings;
    }

    public boolean isAccelerating() {
        return accelerating;
    }

    public void applyConfig(MinecraftServer server) {
        if (server == null) return;
        int pct;
        if ("SIMPLE".equals(settings.string("requirement_mode"))) {
            pct = "ACCELERATE".equals(settings.string("skip_mode"))
                    ? 100 // we time-lapse; vanilla must not instant-jump
                    : clamp(settings.integer("required_sleep_percentage"), 0, 100); // honest display
        } else {
            pct = 100; // VOTE
        }
        setGamerule(server, pct);
    }

    public void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (restoreGameruleAtTick >= 0 && now >= restoreGameruleAtTick) {
            applyConfig(server); // restore to the correct configured value
            restoreGameruleAtTick = -1;
        }

        if (!settings.bool("enabled") || !"SIMPLE".equals(settings.string("requirement_mode"))) {
            if (!sleeping.isEmpty()) sleeping.clear();
            accelerating = false;
            return;
        }

        boolean excludeAfk = settings.bool("exclude_afk_from_requirement");
        boolean accelerate = "ACCELERATE".equals(settings.string("skip_mode"));
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
            String suffix = more > 0 ? " Need " + more + " more." : "";
            for (ServerPlayer p : newlySleeping) {
                server.getPlayerList().broadcastSystemMessage(Component.literal(
                        p.getName().getString() + " is sleeping, " + sleepCount + " of " + required
                                + " required." + suffix), false);
            }
        }

        if (accelerate) {
            ServerLevel ow = server.overworld();
            boolean day = ow != null && ow.isBrightOutside();
            if (accelerating) {
                if (day || sleepCount == 0) {
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        if (p.isSleeping()) p.stopSleepInBed(false, true);
                    }
                    accelerating = false;
                }
            } else if (sleepCount >= required && deep >= 1 && !day) {
                accelerating = true;
            }
        } else {
            accelerating = false;
            // AFK-excluded: drive the skip ourselves once the non-AFK requirement is met (fires
            // no later than vanilla; needed when AFK players would otherwise inflate the count).
            if (excludeAfk && sleepCount >= required && deep >= 1) {
                setGamerule(server, 0);
                restoreGameruleAtTick = now + 10;
            }
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
