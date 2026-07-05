package com.kishku7.ultimatesleep;

import com.kishku7.ultimatesleep.afk.AfkCommandManager;
import com.kishku7.ultimatesleep.afk.AfkManager;
import com.kishku7.ultimatesleep.command.UltimateSleepCommands;
import com.kishku7.ultimatesleep.config.Settings;
import com.kishku7.ultimatesleep.net.UltimateSleepNet;
import com.kishku7.ultimatesleep.permission.SleepPermissions;
import com.kishku7.ultimatesleep.sleep.AutoSleepManager;
import com.kishku7.ultimatesleep.sleep.MobHighlight;
import com.kishku7.ultimatesleep.sleep.RewardManager;
import com.kishku7.ultimatesleep.sleep.SleepEngine;
import com.kishku7.ultimatesleep.sleep.VoteManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
//[[[cog
//import sys; sys.path.insert(0, codegen); import compat_loaders
//for ln in compat_loaders.forge_entry_imports(ver): cog.outl(ln)
//]]]
import net.minecraftforge.common.util.Result;
//[[[end]]]
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ultimate Sleep -- Forge entrypoint. Same wiring as the Fabric/NeoForge entries: settings,
 * permission roster, per-tick managers, the accessibility sleep overrides, the /usleep command
 * tree, and the conditional /afk alias. Shared code references this class by FQCN (per-loader
 * seam), so the static surface is identical to the other loaders.
 *
 * Era drift handled by cog (evidence: javap on the FG6 recomp jars 47.3.0-61.1.0, 2026-07-05):
 *  - EB6 (Forge 47-55): MinecraftForge.EVENT_BUS listeners, base-Event setResult;
 *    ServerTickEvent.Post from Forge 50, Phase idiom at 1.20.1.
 *  - EB7 (Forge 58+): per-event static BUS fields, common.util.Result; tick events become
 *    records at Forge 60 (Post.server() replaces getServer()).
 *
 * Sleep overrides on Forge (no CanPlayerSleepEvent exists on ANY Forge version 47-61):
 *  - sleep_anytime: SleepingTimeCheckEvent (Forge patches the vanilla daytime gate out of
 *    ServerPlayer.startSleepInBed on every version -- the shared mixin's day-gate redirect
 *    target does not exist here; the forge mixin twin drops it).
 *  - highlight_blocking_mobs / sleep_ignore_monsters: ForgeSleepMonstersMixin (the NOT_SAFE
 *    monsters block stays vanilla in the Forge patch, so the redirect is safe on all cells).
 */
@Mod(UltimateSleep.MOD_ID)
public final class UltimateSleep {

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

    public UltimateSleep() {
        LOGGER.info("[UltimateSleep] initializing (Forge) -- commands under /usleep");

        SETTINGS.load();
        PERMISSIONS.load();
        AUTO.load();

        UltimateSleepNet.init();   // Forge builds its channel directly (no register-event)

        //[[[cog
        //import sys; sys.path.insert(0, codegen); import compat_loaders
        //for ln in compat_loaders.forge_entry_wiring(ver): cog.outl(ln)
        //]]]
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> onServerTick(e.server()));
        ServerStartedEvent.BUS.addListener(e -> ENGINE.applyConfig(e.getServer()));
        SleepingTimeCheckEvent.BUS.addListener(this::onSleepingTimeCheck);
        RegisterCommandsEvent.BUS.addListener(this::onRegisterCommands);
        //[[[end]]]

        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.kishku7.ultimatesleep.client.UltimateSleepForgeClient.init();
        }
    }

    private void onServerTick(MinecraftServer server) {
        AFK.tick(server);
        ENGINE.tick(server);
        AUTO.tick(server);
        VOTE.tick(server);
        REWARDS.tick(server);
        MobHighlight.tick(server);
    }

    /**
     * Accessibility: sleep_anytime. Forge replaces the vanilla daytime gate inside
     * startSleepInBed with this event on every version (ForgeEventFactory.onSleepingTimeCheck;
     * at 61 it also swallows the BedRule.canSleep leg). ALLOW bypasses the gate exactly like
     * the shared mixin's day-gate redirect does on Fabric/NeoForge; DEFAULT preserves vanilla.
     */
    private void onSleepingTimeCheck(SleepingTimeCheckEvent event) {
        if (SETTINGS.bool("sleep_anytime")) {
            //[[[cog
            //cog.outl("            event.setResult(%s);" % compat_loaders.forge_sleep_result_expr(ver))
            //]]]
            event.setResult(Result.ALLOW);
            //[[[end]]]
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        var usleep = UltimateSleepCommands.register(event.getDispatcher());
        AFK_COMMANDS.registerAfkCommandIfAbsent(event.getDispatcher(), usleep.getChild("afk"));
    }
}
