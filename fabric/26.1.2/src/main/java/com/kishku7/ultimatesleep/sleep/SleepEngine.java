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
 * Policy (Dave, 2026-06-21): the playersSleepingPercentage gamerule is pinned to 101 so vanilla
 * never skips on its own; the mod owns every skip. INSTANT jumps to morning via requestSkip().
 *
 * ACCELERATE time-lapses the night. In 26.x the day-night cycle is driven by the ServerClockManager
 * (NOT ServerLevel.tickTime(), which only advances gameTime), so we accelerate by setting the
 * overworld clock's RATE: each server tick the clock advances by `rate` instead of 1. We choose the
 * rate so the night finishes in the chosen wall-clock time (Slow/Slowish/Quick/Fast = 10/7.5/5/2.5s)
 * from the moment sleep kicks in -- rate = (ticks remaining to morning) / (seconds * 20). When the
 * morning arrives we restore rate 1.0 and wake the sleepers. Start/end are logged for tuning.
 */
public final class SleepEngine {

    private final Settings settings;
    private final Set<UUID> sleeping = new HashSet<>();
    private volatile boolean accelerating = false;
    private volatile boolean skipPending = false;
    private volatile float accelRate = 1.0f;
    private long accelStartTick = 0;

    public SleepEngine(Settings settings) {
        this.settings = settings;
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

    /** Real seconds the night should take to pass for the named ACCELERATE speed. */
    private static double speedSeconds(String speed) {
        return switch (speed) {
            case "SLOW" -> 10.0;
            case "SLOWISH" -> 7.5;
            case "FAST" -> 2.5;
            default -> 5.0; // QUICK
        };
    }

    private void setClockRate(MinecraftServer server, ServerLevel ow, float rate) {
        ow.dimensionType().defaultClock().ifPresent(clock -> server.clockManager().setRate(clock, rate));
    }

    private void startAccelerate(MinecraftServer server, ServerLevel ow) {
        long remaining = 24000L - (ow.getOverworldClockTime() % 24000L); // day-ticks to morning
        if (remaining < 1) remaining = 1;
        double secs = speedSeconds(settings.string("accelerate_speed"));
        accelRate = (float) Math.max(1.0, remaining / (secs * 20.0));
        accelStartTick = server.getTickCount();
        setClockRate(server, ow, accelRate);
        accelerating = true;
        UltimateSleep.LOGGER.info(String.format(
                "[UltimateSleep] ACCELERATE start: speed=%s target=%.1fs remaining=%d ticks -> clock rate=%.2f/tick (expect ~%.1fs)",
                settings.string("accelerate_speed"), secs, remaining, accelRate, secs));
    }

    private void stopAccelerate(MinecraftServer server) {
        if (!accelerating) return;
        ServerLevel ow = server.overworld();
        if (ow != null) setClockRate(server, ow, 1.0f);
        long elapsed = server.getTickCount() - accelStartTick;
        UltimateSleep.LOGGER.info(String.format(
                "[UltimateSleep] ACCELERATE end: speed=%s elapsed=%d ticks (%.2fs) at rate=%.2f",
                settings.string("accelerate_speed"), elapsed, elapsed / 20.0, accelRate));
        accelerating = false;
        accelRate = 1.0f;
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
            if (accelerating) stopAccelerate(server);
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
                    stopAccelerate(server);
                }
            } else if (sleepCount >= required && deep >= 1 && !day && ow != null) {
                startAccelerate(server, ow);
            }
        } else {
            if (accelerating) stopAccelerate(server);
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
