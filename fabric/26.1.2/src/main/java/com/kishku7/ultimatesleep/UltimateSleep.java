package com.kishku7.ultimatesleep;

import com.kishku7.ultimatesleep.afk.AfkCommandManager;
import com.kishku7.ultimatesleep.afk.AfkManager;
import com.kishku7.ultimatesleep.command.UltimateSleepCommands;
import com.kishku7.ultimatesleep.config.Settings;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ultimate Sleep -- common (server + integrated-server) entrypoint.
 *
 * Responsibilities wired here:
 *   - per-tick AFK activity tracking (AfkManager)
 *   - the /usleep command tree (UltimateSleepCommands)
 *   - conditional registration of a standalone /afk command (AfkCommandManager):
 *     if another mod already provides /afk, ours stands down and the owner is
 *     reported via /usleep admin query.
 *
 * The client-side admin panel lives in the client entrypoint and is not yet
 * implemented (see FUNCTIONAL_SPEC.md).
 */
public final class UltimateSleep implements ModInitializer {

    public static final String MOD_ID = "ultimate_sleep";
    public static final Logger LOGGER = LoggerFactory.getLogger("UltimateSleep");

    private static final Settings SETTINGS = new Settings();
    private static final AfkManager AFK = new AfkManager(SETTINGS);
    private static final AfkCommandManager AFK_COMMANDS = new AfkCommandManager(AFK);

    public static Settings settings() { return SETTINGS; }
    public static AfkManager afk() { return AFK; }
    public static AfkCommandManager afkCommands() { return AFK_COMMANDS; }

    @Override
    public void onInitialize() {
        LOGGER.info("[UltimateSleep] initializing -- commands under /usleep");

        ServerTickEvents.END_SERVER_TICK.register(AFK::tick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            UltimateSleepCommands.register(dispatcher);
            AFK_COMMANDS.registerAfkCommandIfAbsent(dispatcher);
        });
    }
}
