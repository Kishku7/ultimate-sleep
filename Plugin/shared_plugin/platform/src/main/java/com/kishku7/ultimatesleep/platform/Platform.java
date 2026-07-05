package com.kishku7.ultimatesleep.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Runtime platform facade (mirrors the ChunkSmith plugin pattern): ONE jar per major line runs on
 * Paper, Spigot, and Folia; this class detects the flavour and routes scheduling accordingly.
 *
 * Folia has NO Bukkit global scheduler (BukkitScheduler throws UnsupportedOperationException), so
 * the repeating engine tick goes through the GlobalRegionScheduler there, and per-entity mutations
 * (wakeup, potion effects, item gives, sleep injection) go through the entity's region scheduler.
 * The Folia-only classes are referenced ONLY inside {@link Folia}, which is never class-loaded on
 * a non-Folia server, so the same jar still loads on Spigot (which lacks those API classes).
 */
public final class Platform {

    private static final boolean FOLIA =
            classExists("io.papermc.paper.threadedregions.RegionizedServer");

    private Platform() {}

    public static boolean isFolia() {
        return FOLIA;
    }

    /** Repeating main/global tick task, period in ticks. */
    public static void scheduleRepeating(JavaPlugin plugin, Runnable task, long periodTicks) {
        if (FOLIA) {
            Folia.scheduleRepeating(plugin, task, periodTicks);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, 1L, periodTicks);
        }
    }

    /**
     * Run a mutation on the entity's owning thread. On Paper/Spigot our tick already runs on the
     * main thread, so the task runs inline; on Folia it is handed to the entity's region scheduler.
     */
    public static void runOnEntity(JavaPlugin plugin, Entity entity, Runnable task) {
        if (FOLIA) {
            Folia.runOnEntity(plugin, entity, task);
        } else {
            task.run();
        }
    }

    /** Cancel everything this plugin scheduled (used from onDisable). */
    public static void cancelTasks(JavaPlugin plugin) {
        if (FOLIA) {
            Folia.cancelTasks(plugin);
        } else {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
