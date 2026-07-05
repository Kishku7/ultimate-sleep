package com.kishku7.ultimatesleep;

import com.kishku7.ultimatesleep.afk.AfkManager;
import com.kishku7.ultimatesleep.command.UsleepCommand;
import com.kishku7.ultimatesleep.config.PluginSettings;
import com.kishku7.ultimatesleep.listener.SleepListeners;
import com.kishku7.ultimatesleep.permission.SleepPermissions;
import com.kishku7.ultimatesleep.platform.Platform;
import com.kishku7.ultimatesleep.sleep.AutoSleepManager;
import com.kishku7.ultimatesleep.sleep.RewardManager;
import com.kishku7.ultimatesleep.sleep.SleepEngine;
import com.kishku7.ultimatesleep.sleep.VoteManager;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Ultimate Sleep -- the PaperMC/Folia/Spigot server plugin (Bukkit-native reimplementation of the
 * Fabric/NeoForge/Forge mod's server-side feature set; see the functional spec and
 * Plugin/README.md for the parity statement).
 *
 * One jar per MC major line (1.20.x / 1.21.x / 26.x, mirroring the ChunkSmith plugin split); the
 * runtime {@link Platform} facade selects the Paper/Spigot vs Folia scheduler flavour, so each
 * jar covers all three server types for its line.
 *
 * Vanilla-skip ownership: onEnable pins playersSleepingPercentage=101 on every normal-environment
 * world so vanilla never self-skips (the plugin equivalent of the mod's sleep-skip mixin); the
 * original values are restored on disable.
 */
public final class UltimateSleepPlugin extends JavaPlugin {

    private PluginSettings settings;
    private SleepPermissions permissions;
    private AfkManager afk;
    private SleepEngine engine;
    private VoteManager votes;
    private AutoSleepManager autoSleep;
    private RewardManager rewards;

    private volatile long tickCount = 0;
    private boolean gamerulesApplied = false;
    private final Map<String, Integer> originalSleepPct = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new PluginSettings(this);
        settings.load();
        permissions = new SleepPermissions(this);
        permissions.load();
        afk = new AfkManager(settings);
        engine = new SleepEngine(this, settings);
        votes = new VoteManager(this, settings);
        autoSleep = new AutoSleepManager(this, settings);
        rewards = new RewardManager(this, settings);

        getServer().getPluginManager().registerEvents(new SleepListeners(this), this);

        UsleepCommand executor = new UsleepCommand(this);
        wire("usleep", executor);
        wire("afk", executor);

        // Worlds are ready at POSTWORLD enable, but the gamerule guard is applied from the first
        // scheduled tick so it runs on the correct thread on Folia too.
        Platform.scheduleRepeating(this, this::tick, 1L);

        getLogger().info("Ultimate Sleep " + pluginVersion() + " enabled ("
                + (Platform.isFolia() ? "Folia" : "Paper/Spigot") + " scheduler).");
    }

    // Paper deprecates getDescription() in favor of getPluginMeta(), but PluginMeta does not
    // exist on plain Spigot (one jar serves Spigot, Paper, and Folia) -- so the legacy accessor
    // is the correct cross-flavour call here.
    @SuppressWarnings("deprecation")
    private String pluginVersion() {
        return getDescription().getVersion();
    }

    private void wire(String name, UsleepCommand executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
    }

    private void tick() {
        long now = ++tickCount;
        if (!gamerulesApplied) {
            gamerulesApplied = true;
            applyGamerules();
        }
        afk.tick(now);
        engine.tick(now);
        votes.tick(now);
        autoSleep.tick(now);
        rewards.tick(now);
    }

    /**
     * Pin playersSleepingPercentage=101 on normal worlds so the plugin owns every skip.
     *
     * The GameRule constants are deprecated-for-removal on the 1.21.11+/26.x Paper API in favor
     * of the registry lookup, but the registry does not exist on the 1.20.x API line or on plain
     * Spigot -- the constants are the only call that works across every server this one shared
     * source serves, hence the narrow suppression.
     */
    @SuppressWarnings({"deprecation", "removal"})
    private void applyGamerules() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() != World.Environment.NORMAL) continue;
            Integer original = w.getGameRuleValue(GameRule.PLAYERS_SLEEPING_PERCENTAGE);
            // 101 = our own pin left behind by a shutdown that could not restore (Folia's
            // shutdown thread forbids setGameRule) -- treat it as the vanilla default.
            int orig = (original == null || original == 101) ? 100 : original;
            originalSleepPct.put(w.getName(), orig);
            if (!w.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 101)) {
                getLogger().warning("Could not pin playersSleepingPercentage on world "
                        + w.getName() + "; vanilla may self-skip nights there.");
            }
        }
    }

    // GameRule constants: see the applyGamerules() note (no cross-flavour replacement exists).
    @SuppressWarnings({"deprecation", "removal"})
    @Override
    public void onDisable() {
        Platform.cancelTasks(this);
        if (engine != null) engine.shutdown();
        for (World w : Bukkit.getWorlds()) {
            Integer original = originalSleepPct.get(w.getName());
            if (original == null) continue;
            try {
                w.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, original);
            } catch (IllegalStateException e) {
                // Folia: onDisable runs on the RegionShutdownThread, where server settings may
                // not be modified. The pin persists in the world data and is recognized (and
                // treated as vanilla-default) on the next enable, so nothing is lost.
                getLogger().info("Gamerule restore skipped during shutdown (" + e.getMessage()
                        + "); it is re-detected on next enable.");
                break;
            }
        }
        originalSleepPct.clear();
    }

    /** The world whose night is managed (the first normal-environment world). */
    public World primaryWorld() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) return w;
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    /** Server-wide "[Ultimate Sleep] ..." line to every player + the console. */
    public void broadcast(String message) {
        String line = "[Ultimate Sleep] " + message;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(line);
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    public long tickCount() { return tickCount; }
    public PluginSettings settings() { return settings; }
    public SleepPermissions permissions() { return permissions; }
    public AfkManager afk() { return afk; }
    public SleepEngine engine() { return engine; }
    public VoteManager votes() { return votes; }
    public AutoSleepManager autoSleep() { return autoSleep; }
    public RewardManager rewards() { return rewards; }
}
