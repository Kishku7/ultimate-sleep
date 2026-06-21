package com.kishku7.ultimatesleep;

import com.kishku7.ultimatesleep.afk.AfkCommandManager;
import com.kishku7.ultimatesleep.afk.AfkManager;
import com.kishku7.ultimatesleep.command.UltimateSleepCommands;
import com.kishku7.ultimatesleep.config.Settings;
import com.kishku7.ultimatesleep.permission.SleepPermissions;
import com.kishku7.ultimatesleep.sleep.AutoSleepManager;
import com.kishku7.ultimatesleep.sleep.RewardManager;
import com.kishku7.ultimatesleep.sleep.SleepEngine;
import com.kishku7.ultimatesleep.sleep.VoteManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.util.EventResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ultimate Sleep -- common (server + integrated-server) entrypoint.
 *
 * Wires: settings (load/persist), the sleep-admin permission roster, the per-tick managers (AFK
 * tracking + notifications, SIMPLE-mode engine + messaging + AFK-excluded skip, auto-sleep at
 * dusk, VOTE-mode voting, rewards-on-wake), the accessibility sleep-check overrides
 * (EntitySleepEvents), the /usleep command tree, and the conditional /afk redirect alias. The
 * mode-dependent vanilla gamerule is applied on server start.
 *
 * The client admin panel + vote popup are deferred to the last phase before 1.0 (FUNCTIONAL_SPEC.md).
 */
public final class UltimateSleep implements ModInitializer {

    public static final String MOD_ID = "ultimate_sleep";
    public static final Logger LOGGER = LoggerFactory.getLogger("UltimateSleep");

    private static final Settings SETTINGS = new Settings();
    private static final AfkManager AFK = new AfkManager(SETTINGS);
    private static final AfkCommandManager AFK_COMMANDS = new AfkCommandManager(AFK);
    private static final SleepPermissions PERMISSIONS = new SleepPermissions();
    private static final SleepEngine ENGINE = new SleepEngine(SETTINGS);
    private static final AutoSleepManager AUTO = new AutoSleepManager(SETTINGS);
    private static final VoteManager VOTE = new VoteManager(SETTINGS);
    private static final RewardManager REWARDS = new RewardManager(SETTINGS);

    public static Settings settings() { return SETTINGS; }
    public static AfkManager afk() { return AFK; }
    public static AfkCommandManager afkCommands() { return AFK_COMMANDS; }
    public static SleepPermissions permissions() { return PERMISSIONS; }
    public static SleepEngine engine() { return ENGINE; }
    public static AutoSleepManager autoSleep() { return AUTO; }
    public static VoteManager vote() { return VOTE; }

    @Override
    public void onInitialize() {
        LOGGER.info("[UltimateSleep] initializing -- commands under /usleep");

        SETTINGS.load();
        PERMISSIONS.load();
        AUTO.load();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            AFK.tick(server);
            ENGINE.tick(server);
            AUTO.tick(server);
            VOTE.tick(server);
            REWARDS.tick(server);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(ENGINE::applyConfig);

        // Accessibility: let players sleep with nearby monsters when enabled.
        // (sleep_anytime has no event in 26.x fabric-api -- time is gated by BedRule; needs a mixin, deferred.)
        EntitySleepEvents.ALLOW_NEARBY_MONSTERS.register((player, sleepingPos, vanillaResult) -> {
            if (SETTINGS.bool("sleep_ignore_monsters")) return EventResult.ALLOW;
            return EventResult.PASS;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var usleep = UltimateSleepCommands.register(dispatcher);
            AFK_COMMANDS.registerAfkCommandIfAbsent(dispatcher, usleep.getChild("afk"));
        });
    }
}
