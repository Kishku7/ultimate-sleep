package com.kishku7.ultimatesleep.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Folia-only scheduler calls. This class references Folia/Paper region-scheduler API and is ONLY
 * class-loaded when {@link Platform} has detected Folia, so the jar still loads on Spigot.
 */
final class Folia {

    private Folia() {}

    static void scheduleRepeating(JavaPlugin plugin, Runnable task, long periodTicks) {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), 1L, periodTicks);
    }

    static void runOnEntity(JavaPlugin plugin, Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, t -> task.run(), null);
    }

    static void cancelTasks(JavaPlugin plugin) {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }
}
