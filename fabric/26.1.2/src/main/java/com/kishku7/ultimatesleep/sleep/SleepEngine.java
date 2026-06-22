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
 * Policy (Dave, 2026-06-21): the vanilla playersSleepingPercentage gamerule is PINNED to 101 and
 * left there. With 101, sleepersNeeded always rounds up to (activePlayers + 1) -- unreachable --
 * so vanilla never skips the night by itself even if every player piles into bed. The ONLY trigger
 * is this mod. INSTANT skips are driven through {@link #requestSkip()}: when the configured share
 * of eligible players is deep-asleep we raise the pending flag, and ServerLevelSleepSkipMixin lets
 * vanilla's own skip block run (clock advance + wake + weather) for that one tick. ACCELERATE mode
 * instead time-lapses the night via ServerLevelTimeMixin and never requests a skip. VOTE mode is
 * handled by VoteManager, which also calls requestSkip() on a passing vote.
 */
public final class SleepEngine {

    private final Settings settings;
    private final Set<UUID> sleeping = new HashSet<>();
    private volatile boolean accelerating = false;
    private volatile boolean skipPending = false;
    private volatile int accelMultiplier = 1;

    public SleepEngine(Settings settings) {
        this.settings = settings;
    }

    /** Game-ticks of time to advance per real tick while accelerating (computed per skip). */
    public int accelMultiplier() {
        return accelMultiplier;
    }

    /** Real seconds the night should take to pass for the named ACCELERATE speed. */
    private static double speedSeconds(String speed) {
        return switch (speed) {
            case "SLOW" -> 10.0;
            case "SLOWISH" -> 7.5;
            case "FAST" -> 2.5;
            default -> 5.0; // QUICK
        };
    }

    /**
     * Pace the time-lapse so the night finishes in the chosen real-time duration, from the moment
     * sleep kicks in: advance (ticks remaining until morning) over (seconds * 20) real ticks.
     */
    private void computeAccelMultiplier(ServerLevel ow) {
        long remaining = 24000L - (ow.getOverworldClockTime() % 24000L); // ticks to the morning reset
        if (remaining < 1) remaining = 1;
        int realTicks = Math.max(1, (int) Math.round(speedSeconds(settings.string("accelerate_speed")) * 20.0));
        accelMultiplier = Math.max(1, (int) Math.round((double) remaining / realTicks));
    }

    public boolean isAccelerating() {
        return accelerating;
    }

    /** Ask the skip mixin to let vanilla advance the night on the next overworld tick. */
    public void requestSkip() {
        this.skipPending = true;
    }

    public boolean isSkipPending() {
        return this.skipPending;
    }

    public void consumeSkip() {
        this.skipPending = false;
    }

    /**
     * Pin the gamerule to 101 so vanilla can NEVER self-trigger -- sleepersNeeded always rounds to
     * (activePlayers + 1), which is unreachable, so even if every player piles into bed the vanilla
     * skip stays dormant. The mod owns every skip (via the skip mixin's pending flag).
     */
    public void applyConfig(MinecraftServer server) {
        if (server == null) return;
        setGamerule(server, 101);
    }

    public void tick(MinecraftServer server) {
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
            } else if (sleepCount >= required && deep >= 1 && !day && ow != null) {
                computeAccelMultiplier(ow);
                accelerating = true;
            }
        } else {
            accelerating = false;
            // INSTANT: the mod drives the skip the moment the configured share is deep-asleep.
            if (sleepCount >= required && deep >= 1) {
                requestSkip();
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
