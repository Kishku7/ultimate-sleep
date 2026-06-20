package com.kishku7.ultimatesleep.afk;

import com.kishku7.ultimatesleep.config.Settings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which players are AFK.
 *
 * Auto-AFK: a player is considered AFK after {@code afk_threshold_seconds} with
 * no change in position or look direction. Manual AFK: a player (or our /afk
 * command, when active) toggles AFK explicitly; any movement clears both the
 * auto and manual flags.
 *
 * State is rebuilt against the currently-online players each tick, so logged-off
 * players are dropped automatically.
 */
public final class AfkManager {

    private static final class State {
        double x, y, z;
        float yaw, pitch;
        int lastActiveTick;
        boolean auto;    // set by the idle timer
        boolean manual;  // toggled explicitly

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

    /** Toggle manual AFK for a player. @return the resulting AFK state. */
    public boolean toggleManual(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        int now = server != null ? server.getTickCount() : 0;
        State s = states.computeIfAbsent(p.getUUID(), k -> snapshot(new State(), p, now));
        s.manual = !s.manual;
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
