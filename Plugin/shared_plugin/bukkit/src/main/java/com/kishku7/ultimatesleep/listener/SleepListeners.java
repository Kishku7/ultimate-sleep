package com.kishku7.ultimatesleep.listener;

import com.kishku7.ultimatesleep.UltimateSleepPlugin;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Event wiring: accessibility bed-enter overrides + AFK activity signals.
 *
 * Accessibility (the functional spec; the mod does these via ServerPlayerSleepMixin redirects --
 * Bukkit exposes the SAME decisions as PlayerBedEnterEvent.BedEnterResult overrides):
 *   - NOT_POSSIBLE_NOW (daytime)  + sleep_anytime        -> allow.
 *   - NOT_SAFE (monsters nearby)  + sleep_ignore_monsters -> allow.
 *   - TOO_FAR_AWAY                + ignore_bed_too_far    -> allow.
 * highlight_blocking_mobs has NO plugin equivalent (per-player client rendering) -- dropped,
 * see Plugin/README.md.
 *
 * AFK: movement/look and interaction feed AfkManager timestamps; activity while sleeping is
 * ignored (being in bed never cancels AFK -- same as the mod).
 */
public final class SleepListeners implements Listener {

    private final UltimateSleepPlugin plugin;

    public SleepListeners(UltimateSleepPlugin plugin) {
        this.plugin = plugin;
    }

    // getBedEnterResult() is deprecated on newer Paper in favor of enterAction(), but
    // BedEnterAction is Paper-only and absent from the older API lines and plain Spigot --
    // the legacy result enum is the only cross-flavour decision surface.
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.NORMAL)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (!plugin.settings().bool("enabled")) return;
        switch (event.getBedEnterResult()) {
            case NOT_POSSIBLE_NOW -> {
                if (plugin.settings().bool("sleep_anytime")) event.setUseBed(Event.Result.ALLOW);
            }
            case NOT_SAFE -> {
                if (plugin.settings().bool("sleep_ignore_monsters")) event.setUseBed(Event.Result.ALLOW);
            }
            case TOO_FAR_AWAY -> {
                if (plugin.settings().bool("ignore_bed_too_far")) event.setUseBed(Event.Result.ALLOW);
            }
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getPlayer().isSleeping()) return; // bed movement never cancels AFK
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        boolean changed = from.getX() != to.getX() || from.getY() != to.getY()
                || from.getZ() != to.getZ()
                || from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch();
        if (changed) {
            plugin.afk().markActive(event.getPlayer(), plugin.tickCount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getPlayer().isSleeping()) return;
        plugin.afk().markActive(event.getPlayer(), plugin.tickCount());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.afk().remove(event.getPlayer().getUniqueId());
    }
}
