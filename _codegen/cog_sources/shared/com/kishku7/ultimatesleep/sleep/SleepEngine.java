package com.kishku7.ultimatesleep.sleep;

import com.kishku7.ultimatesleep.compat.Era;

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
 * Night-skip engine: decides WHEN to skip (SIMPLE percentage path) and performs HOW every skip is
 * carried out (INSTANT or ACCELERATE), for both SIMPLE and VOTE requirement modes.
 *
 * The mod owns every skip: vanilla never self-skips because ServerLevelSleepSkipMixin redirects the
 * overworld sleep check to our skipPending flag (the playersSleepingPercentage gamerule is not
 * touched -- it is rejected by the 26.x command parser and is unnecessary given the mixin).
 *
 * performSkip() is the single entry point used by SIMPLE (here) and VOTE (VoteManager):
 *   - INSTANT  -> requestSkip(): the mixin lets vanilla jump to morning (+wake +weather).
 *   - ACCELERATE -> set the overworld clock RATE so the night time-lapses to dawn in the chosen
 *     wall-clock seconds (Slow/Slowish/Quick/Fast = 10/7.5/5/2.5s), then restore rate 1.0 and wake.
 * In 26.x the day-night cycle is driven by ServerClockManager (NOT tickTime, which only moves
 * gameTime), so the clock RATE is the correct lever. The acceleration loop runs every tick in any
 * mode; start/end are logged for tuning.
 */
public final class SleepEngine {

    private final Settings settings;
    private final Set<UUID> sleeping = new HashSet<>();
    private volatile boolean accelerating = false;
    private volatile boolean skipPending = false;
    private volatile float accelRate = 1.0f;
    private long accelStartTick = 0;
    // Absolute day-clock time the acceleration must reach (start clock + ticks-to-morning).
    private long accelTargetTime = 0;
    // Real-tick failsafe. Acceleration may NEVER outlive this, whatever the world is doing.
    private long accelDeadlineTick = 0;
    // Set when a mod-driven skip begins (INSTANT or ACCELERATE); drives the one-shot notify_wake
    // broadcast that fires when morning actually arrives.
    private volatile boolean awaitingMorning = false;

    public SleepEngine(Settings settings) {
        this.settings = settings;
    }

    public boolean isAccelerating() {
        return accelerating;
    }

    /** Ask the skip mixin to let vanilla advance the night on the next overworld tick. */
    public void requestSkip() {
        this.skipPending = true;
        this.awaitingMorning = true;
    }

    public boolean isSkipPending() {
        return this.skipPending;
    }

    public void consumeSkip() {
        this.skipPending = false;
    }

    /** Carry out a skip per skip_mode. Safe to call repeatedly; no-op while already accelerating. */
    public void performSkip(MinecraftServer server) {
        if (accelerating) return;
        ServerLevel ow = server.overworld();
        if ("ACCELERATE".equals(settings.string("skip_mode")) && ow != null) {
            startAccelerate(server, ow);
        } else {
            requestSkip();
        }
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
        Era.setClockRate(server, ow, rate);
    }

    private void startAccelerate(MinecraftServer server, ServerLevel ow) {
        long remaining = Era.ticksToMorning(ow);
        double secs = speedSeconds(settings.string("accelerate_speed"));
        accelRate = (float) Math.max(1.0, remaining / (secs * 20.0));
        accelStartTick = server.getTickCount();
        accelTargetTime = Era.clockTime(ow) + remaining;
        // Belt and braces: 3x the intended wall-clock duration + 10s. Nothing is allowed to leave
        // the world clock running fast -- the rate is SavedData and survives a restart.
        accelDeadlineTick = accelStartTick + (long) Math.ceil(secs * 20.0 * 3.0) + 200L;
        setClockRate(server, ow, accelRate);
        accelerating = true;
        awaitingMorning = true;
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
     * One-shot wake broadcast: once a mod-driven skip has reached morning, announce it (if
     * notify_wake is on) and clear the flag. Fires for both INSTANT (vanilla jumped to dawn) and
     * ACCELERATE (time-lapse finished). Runs every tick before the accelerating early-return.
     */
    private void notifyWakeIfDue(MinecraftServer server) {
        if (!awaitingMorning) return;
        ServerLevel ow = server.overworld();
        if (ow != null && !Era.dayPhase(ow)) return; // not morning yet (CLOCK, not sky light)
        awaitingMorning = false;
        if (settings.bool("notify_wake")) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "[Ultimate Sleep] Good morning -- the night has passed."), false);
        }
    }

    /**
     * While accelerating, hand the last stretch of the night back to the vanilla skip once the
     * CLOCK says dawn is near, then restore rate 1.0. Runs every tick, any mode.
     *
     * The end condition is the day CLOCK and NEVER bright()/isBrightOutside(). Sky light is pushed
     * below the daylight threshold by rain and (hard) by thunder at any time of day, so a
     * brightness end condition never fires during a storm -- mod_support #10's follow-up report:
     * the clock rate stayed cranked FOREVER (it is SavedData, so it survived restarts and had to be
     * repaired by hand with /tick freeze + /time add), and because `accelerating` never cleared,
     * the engine stopped evaluating anything at all, so no later sleep did anything either.
     *
     * Ending through requestSkip() rather than just restoring the rate means ACCELERATE finishes in
     * exactly the same vanilla block INSTANT uses, so wakeUpAllPlayers, the weather reset
     * (preserve_weather) and the world-progression mixin fire identically in BOTH skip modes.
     * Previously ACCELERATE bypassed all three: storms outlived the night whatever preserve_weather
     * said, and nothing progressed.
     */
    private void manageAcceleration(MinecraftServer server) {
        if (!accelerating) return;
        ServerLevel ow = server.overworld();
        if (ow != null) Era.accelStep(server, ow, accelRate); // pre-26: advances dayTime; 26: no-op

        // A single clock step is `accelRate` ticks wide (hundreds at FAST), so leave a margin
        // rather than testing for an exact landing on the wake-up marker at clock 0.
        long margin = Math.max(200L, (long) Math.ceil(accelRate) * 2L);
        boolean nearDawn = ow == null || Era.clockTime(ow) >= accelTargetTime - margin;
        boolean timedOut = server.getTickCount() >= accelDeadlineTick;
        if (!nearDawn && !timedOut) return;

        if (timedOut && !nearDawn) {
            UltimateSleep.LOGGER.warn("[UltimateSleep] ACCELERATE failsafe: deadline reached before "
                    + "dawn -- restoring clock rate 1.0 and finishing the skip.");
        }
        stopAccelerate(server);
        requestSkip(); // vanilla finishes the night: wake + weather + progression
    }

    /**
     * Server-start hook (wired to SERVER_STARTED on all three loaders).
     *
     * We still do NOT touch the playersSleepingPercentage gamerule: the vanilla command
     * `gamerule playersSleepingPercentage <n>` is rejected in 26.x (Incorrect argument), and it's
     * unnecessary anyway -- ServerLevelSleepSkipMixin overrides the overworld sleep check, so
     * vanilla never self-skips regardless of the gamerule value. What this DOES do now is repair a
     * stuck world clock rate; see below.
     */
    public void applyConfig(MinecraftServer server) {
        // Drop any catch-up slice left in flight by a crash (the statics outlive a world in
        // singleplayer), so a fresh world never inherits one.
        ProgressionState.reset();
        // Clock-rate self-heal (mod_support #10 follow-up). ServerClockManager extends SavedData,
        // so an accelerated rate is written to disk and survives a restart. If acceleration is ever
        // interrupted -- a crash, a kill, or the pre-1.2.7 storm deadlock -- the world is left
        // permanently time-lapsing with no vanilla mechanism to put it back. Nothing else resets
        // it, so force 1.0 on every server start. Harmless when it is already 1.0.
        if (accelerating) return;
        ServerLevel ow = server.overworld();
        if (ow != null) setClockRate(server, ow, 1.0f);
    }

    public void tick(MinecraftServer server) {
        notifyWakeIfDue(server);
        manageAcceleration(server);
        // Don't evaluate new triggers while a skip is in flight: time-lapsing (ACCELERATE) or a
        // skip already handed to vanilla and not yet consumed (INSTANT, and the ACCELERATE handoff
        // -- without skipPending here the same tick would immediately start a second acceleration).
        if (accelerating || skipPending) return;

        if (!settings.bool("enabled") || !"SIMPLE".equals(settings.string("requirement_mode"))) {
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
            String suffix = more > 0 ? " Need " + more + " more." : "";
            for (ServerPlayer p : newlySleeping) {
                server.getPlayerList().broadcastSystemMessage(Component.literal(
                        p.getName().getString() + " is sleeping, " + sleepCount + " of " + required
                                + " required." + suffix), false);
            }
        }

        if (sleepCount >= required && deep >= 1) {
            performSkip(server); // respects skip_mode (INSTANT or ACCELERATE)
        }

        sleeping.clear();
        sleeping.addAll(current);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
