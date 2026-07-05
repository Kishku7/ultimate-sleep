package com.kishku7.ultimatesleep.sleep;

import com.kishku7.ultimatesleep.UltimateSleepPlugin;
import com.kishku7.ultimatesleep.config.PluginSettings;
import com.kishku7.ultimatesleep.platform.Platform;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Night-skip engine (Bukkit port of the mod's SleepEngine): decides WHEN to skip (SIMPLE
 * percentage path) and performs HOW every skip is carried out (INSTANT or ACCELERATE), for both
 * SIMPLE and VOTE requirement modes.
 *
 * The plugin owns every skip: vanilla never self-skips because onEnable pins the
 * playersSleepingPercentage gamerule to 101 on every normal-environment world (the Bukkit
 * equivalent of the mod's ServerLevelSleepSkipMixin override; restored on disable).
 *
 * performSkip() is the single entry point used by SIMPLE (here) and VOTE (VoteManager):
 *   - INSTANT   -> jump the primary world's full time to the next dawn (+wake +weather).
 *   - ACCELERATE -> step the clock every tick so the night time-lapses to dawn in the chosen
 *     wall-clock seconds (SLOW/SLOWISH/QUICK/FAST = 10/7.5/5/2.5s), then wake.
 * On Folia the per-tick stepping runs on the GlobalRegionScheduler (world time is global state);
 * per-player wakes are routed through the entity scheduler via the Platform facade.
 */
public final class SleepEngine {

    /** Vanilla "sleep allowed" window is [12542, 23459]; outside it counts as bright/morning. */
    public static boolean bright(World w) {
        long t = w.getTime();
        return t < 12542L || t > 23459L;
    }

    /** Ticks until the next multiple of 24000 (vanilla's morning target); 0 at exact dawn. */
    public static long ticksToMorning(World w) {
        return (24000L - (w.getFullTime() % 24000L)) % 24000L;
    }

    private final UltimateSleepPlugin plugin;
    private final PluginSettings settings;
    private final Progression progression;
    private final Set<UUID> sleeping = new HashSet<>();
    private boolean accelerating = false;
    private double accelRate = 1.0;
    private long accelStartTick = 0;
    // Set when a plugin-driven skip begins; drives the one-shot notify_wake broadcast.
    private boolean awaitingMorning = false;

    public SleepEngine(UltimateSleepPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.progression = new Progression(plugin, settings);
    }

    public boolean isAccelerating() {
        return accelerating;
    }

    /** Carry out a skip per skip_mode. Safe to call repeatedly; no-op while already accelerating. */
    public void performSkip() {
        if (accelerating) return;
        World w = plugin.primaryWorld();
        if (w == null) return;
        if ("ACCELERATE".equals(settings.string("skip_mode"))) {
            startAccelerate(w);
        } else {
            instantSkip(w);
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

    private void instantSkip(World w) {
        long remaining = ticksToMorning(w);
        if (remaining <= 0) return;
        progression.begin(w, remaining);
        w.setFullTime(w.getFullTime() + remaining);
        if (!settings.bool("preserve_weather")) {
            w.setStorm(false);
            w.setThundering(false);
        }
        wakeSleepers(w);
        awaitingMorning = true;
    }

    private void startAccelerate(World w) {
        long remaining = ticksToMorning(w);
        if (remaining <= 0) return;
        double secs = speedSeconds(settings.string("accelerate_speed"));
        accelRate = Math.max(1.0, remaining / (secs * 20.0));
        accelStartTick = plugin.tickCount();
        accelerating = true;
        awaitingMorning = true;
        progression.begin(w, remaining);
        plugin.getLogger().info(String.format(
                "ACCELERATE start: speed=%s target=%.1fs remaining=%d ticks -> +%.2f ticks/tick",
                settings.string("accelerate_speed"), secs, remaining, accelRate));
    }

    private void stopAccelerate() {
        if (!accelerating) return;
        long elapsed = plugin.tickCount() - accelStartTick;
        plugin.getLogger().info(String.format(
                "ACCELERATE end: speed=%s elapsed=%d ticks (%.2fs) at +%.2f ticks/tick",
                settings.string("accelerate_speed"), elapsed, elapsed / 20.0, accelRate));
        accelerating = false;
        accelRate = 1.0;
    }

    /** One-shot wake broadcast once a plugin-driven skip has reached morning. */
    private void notifyWakeIfDue(World w) {
        if (!awaitingMorning || !bright(w)) return;
        awaitingMorning = false;
        if (settings.bool("notify_wake")) {
            plugin.broadcast("Good morning -- the night has passed.");
        }
    }

    /** While accelerating, step the clock and stop + wake everyone once dawn arrives. */
    private void manageAcceleration(World w) {
        if (!accelerating) return;
        if (!bright(w)) {
            long step = (long) Math.ceil(accelRate);
            long remaining = ticksToMorning(w);
            w.setFullTime(w.getFullTime() + Math.min(step, remaining));
        }
        if (bright(w)) {
            if (!settings.bool("preserve_weather")) {
                w.setStorm(false);
                w.setThundering(false);
            }
            wakeSleepers(w);
            stopAccelerate();
        }
    }

    private void wakeSleepers(World w) {
        for (Player p : w.getPlayers()) {
            if (!p.isSleeping()) continue;
            Platform.runOnEntity(plugin, p, () -> {
                if (p.isSleeping()) p.wakeup(false);
            });
        }
    }

    public void tick(long now) {
        World w = plugin.primaryWorld();
        if (w == null) return;
        notifyWakeIfDue(w);
        manageAcceleration(w);
        progression.tick();
        if (accelerating) return; // night is time-lapsing; don't evaluate new triggers

        if (!settings.bool("enabled") || !"SIMPLE".equals(settings.string("requirement_mode"))) {
            if (!sleeping.isEmpty()) sleeping.clear();
            return;
        }

        boolean excludeAfk = settings.bool("exclude_afk_from_requirement");
        int pct = Math.max(0, Math.min(100, settings.integer("required_sleep_percentage")));

        int eligible = 0, sleepCount = 0, deep = 0;
        Set<UUID> current = new HashSet<>();
        List<Player> newlySleeping = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (excludeAfk && plugin.afk().isAfk(p.getUniqueId())) continue;
            eligible++;
            if (p.isSleeping()) {
                sleepCount++;
                current.add(p.getUniqueId());
                if (p.getSleepTicks() >= 100) deep++; // vanilla isSleepingLongEnough
                if (!sleeping.contains(p.getUniqueId())) newlySleeping.add(p);
            }
        }

        int required = Math.max(1, (int) Math.ceil(eligible * pct / 100.0));

        if (settings.bool("show_sleepers_in_chat") && !newlySleeping.isEmpty()) {
            int more = Math.max(0, required - sleepCount);
            String suffix = more > 0 ? " Need " + more + " more." : "";
            for (Player p : newlySleeping) {
                plugin.broadcast(p.getName() + " is sleeping, " + sleepCount + " of " + required
                        + " required." + suffix);
            }
        }

        if (sleepCount >= required && deep >= 1) {
            performSkip(); // respects skip_mode (INSTANT or ACCELERATE)
        }

        sleeping.clear();
        sleeping.addAll(current);
    }

    /** Restore anything the engine changed mid-skip (called from onDisable). */
    public void shutdown() {
        progression.restore();
        accelerating = false;
    }
}
