package com.kishku7.ultimatesleep.afk;

import com.kishku7.ultimatesleep.config.PluginSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players are AFK and notifies them on EVERY status change (Bukkit port of the mod's
 * AfkManager).
 *
 * Auto-AFK: no movement/look/interaction event for afk_threshold_seconds. Manual AFK: /usleep afk,
 * the /afk alias, or /usleep admin afk. Cancellation: any NON-bed activity clears AFK; being in a
 * bed / sleeping does not (listeners skip activity while asleep).
 *
 * Instead of polling positions each tick (the mod's approach, which on Folia would mean cross-
 * region reads), activity is fed by events (PlayerMoveEvent / PlayerInteractEvent), which fire on
 * the correct region thread -- hence the concurrent map.
 *
 * Notification is centralized in tick() (single source of truth): each tick a player's current
 * AFK state is compared to the last state they were told, and a message is sent on any
 * transition. Commands do NOT message the affected player themselves except via notify().
 */
public final class AfkManager {

    private static final class State {
        volatile long lastActiveTick;
        volatile boolean auto;      // set by the idle timer
        volatile boolean manual;    // set explicitly
        volatile boolean notified;  // last AFK state announced to the player

        boolean afk() { return auto || manual; }
    }

    private final PluginSettings settings;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public AfkManager(PluginSettings settings) {
        this.settings = settings;
    }

    /** Activity signal from the listeners (move/look/interact while NOT sleeping). */
    public void markActive(Player p, long nowTick) {
        State s = states.computeIfAbsent(p.getUniqueId(), k -> new State());
        s.lastActiveTick = nowTick;
        s.auto = false;
        s.manual = false;
    }

    /** Called every tick from the global loop. */
    public void tick(long nowTick) {
        if (!settings.bool("enabled")) return;
        final long thresholdTicks = Math.max(1, settings.integer("afk_threshold_seconds")) * 20L;

        for (Player p : Bukkit.getOnlinePlayers()) {
            State s = states.computeIfAbsent(p.getUniqueId(), k -> {
                State n = new State();
                n.lastActiveTick = nowTick;
                return n;
            });
            if (!p.isSleeping() && !s.manual && !s.auto
                    && (nowTick - s.lastActiveTick) >= thresholdTicks) {
                s.auto = true;
            }
            boolean afk = s.afk();
            if (afk != s.notified) {
                p.sendMessage(afk
                        ? "[Ultimate Sleep] You are now AFK."
                        : "[Ultimate Sleep] You are no longer AFK.");
                s.notified = afk;
            }
        }
    }

    public boolean isAfk(UUID id) {
        State s = states.get(id);
        return s != null && s.afk();
    }

    /** Toggle manual AFK. @return resulting AFK state. */
    public boolean toggleManual(Player p, long nowTick) {
        State s = states.computeIfAbsent(p.getUniqueId(), k -> new State());
        s.manual = !s.manual;
        if (!s.manual) {
            s.auto = false;
            s.lastActiveTick = nowTick;
        }
        notify(p, s);
        return s.afk();
    }

    /** Explicitly set manual AFK (used by /usleep admin afk). @return resulting AFK state. */
    public boolean setManual(Player p, boolean afk, long nowTick) {
        State s = states.computeIfAbsent(p.getUniqueId(), k -> new State());
        s.manual = afk;
        if (!afk) {
            s.auto = false;
            s.lastActiveTick = nowTick;
        }
        notify(p, s);
        return s.afk();
    }

    public void remove(UUID id) {
        states.remove(id);
    }

    /** Immediately tell the player their current AFK state and mark it notified (avoids tick dup). */
    private void notify(Player p, State s) {
        boolean afk = s.afk();
        p.sendMessage(afk
                ? "[Ultimate Sleep] You are now AFK."
                : "[Ultimate Sleep] You are no longer AFK.");
        s.notified = afk;
    }

    public int afkCount() {
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isAfk(p.getUniqueId())) n++;
        }
        return n;
    }
}
