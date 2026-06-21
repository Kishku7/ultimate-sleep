package com.kishku7.ultimatesleep.afk;

import com.kishku7.ultimatesleep.config.Settings;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which players are AFK and notifies them on EVERY status change.
 *
 * Auto-AFK: no change in position/look for {@code afk_threshold_seconds}. Manual AFK: set via
 * /usleep afk, the /afk alias, or /usleep admin afk. Cancellation: any NON-bed movement clears
 * AFK; being in a bed / sleeping does not (we skip the movement check while asleep).
 *
 * Notification is centralized here (single source of truth): each tick we compare a player's
 * current AFK state to the last state we told them, and send a message on any transition -- so
 * losing AFK by moving (or any other reason) always notifies. Commands therefore do NOT message
 * the affected player themselves.
 */
public final class AfkManager {

    private static final class State {
        double x, y, z;
        float yaw, pitch;
        int lastActiveTick;
        boolean auto;      // set by the idle timer
        boolean manual;    // set explicitly
        boolean notified;  // last AFK state announced to the player

        boolean afk() { return auto || manual; }
    }

    private final Settings settings;
    private final Map<UUID, State> states = new HashMap<>();

    public AfkManager(Settings settings) {
        this.settings = settings;
    }

    /** Called every server tick. */
    public void tick(MinecraftServer server) {
        if (!settings.bool("enabled")) return;

        final int now = server.getTickCount();
        final int thresholdTicks = Math.max(1, settings.integer("afk_threshold_seconds")) * 20;

        Set<UUID> online = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            online.add(id);
            State s = states.computeIfAbsent(id, k -> snapshot(new State(), p, now));

            // Being in a bed / sleeping never cancels AFK -> skip the movement check while asleep.
            if (!p.isSleeping()) {
                boolean moved = p.getX() != s.x || p.getY() != s.y || p.getZ() != s.z
                        || p.getYRot() != s.yaw || p.getXRot() != s.pitch;
                if (moved) {
                    snapshot(s, p, now);
                    s.auto = false;
                    s.manual = false;
                } else if (!s.manual && (now - s.lastActiveTick) >= thresholdTicks) {
                    s.auto = true;
                }
            }

            // Notify on any transition (single source of truth).
            boolean afk = s.afk();
            if (afk != s.notified) {
                p.sendSystemMessage(Component.literal(afk
                        ? "[Ultimate Sleep] You are now AFK."
                        : "[Ultimate Sleep] You are no longer AFK."));
                s.notified = afk;
            }
        }
        states.keySet().retainAll(online);
    }

    private static State snapshot(State s, ServerPlayer p, int now) {
        s.x = p.getX(); s.y = p.getY(); s.z = p.getZ();
        s.yaw = p.getYRot(); s.pitch = p.getXRot();
        s.lastActiveTick = now;
        return s;
    }

    public boolean isAfk(UUID id) {
        State s = states.get(id);
        return s != null && s.afk();
    }

    private State stateFor(ServerPlayer p) {
        int now = p.level().getServer() != null ? p.level().getServer().getTickCount() : 0;
        return states.computeIfAbsent(p.getUUID(), k -> snapshot(new State(), p, now));
    }

    /** Toggle manual AFK. The player is notified by the next tick. @return resulting AFK state. */
    public boolean toggleManual(ServerPlayer p) {
        State s = stateFor(p);
        s.manual = !s.manual;
        return s.afk();
    }

    /** Explicitly set manual AFK (used by /usleep admin afk). @return resulting AFK state. */
    public boolean setManual(ServerPlayer p, boolean afk) {
        State s = stateFor(p);
        s.manual = afk;
        if (!afk) s.auto = false;
        return s.afk();
    }

    public int afkCount(MinecraftServer server) {
        int n = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isAfk(p.getUUID())) n++;
        }
        return n;
    }
}
