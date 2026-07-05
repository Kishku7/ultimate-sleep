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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ultimate Sleep -- NeoForge entrypoint. Same wiring as the Fabric entry: settings, permission
 * roster, per-tick managers, the accessibility sleep-event overrides, the /usleep command tree,
 * and the conditional /afk alias. Shared code references this class by FQCN (per-loader seam).
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

    public UltimateSleep(ModContainer mod, IEventBus bus, Dist dist) {
        LOGGER.info("[UltimateSleep] initializing (NeoForge) -- commands under /usleep");

        SETTINGS.load();
        PERMISSIONS.load();
        AUTO.load();

        bus.addListener(UltimateSleepNet::registerPayloads);

        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onCanSleep);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        if (dist.isClient()) {
            com.kishku7.ultimatesleep.client.UltimateSleepNeoForgeClient.init();
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        AFK.tick(server);
        ENGINE.tick(server);
        AUTO.tick(server);
        VOTE.tick(server);
        REWARDS.tick(server);
        MobHighlight.tick(server);
    }

    private void onServerStarted(ServerStartedEvent event) {
        ENGINE.applyConfig(event.getServer());
    }

    /**
     * Accessibility overrides, mirroring Fabric's EntitySleepEvents.ALLOW_NEARBY_MONSTERS:
     * optionally glow the blocking monsters for the sleeper only, and optionally clear the
     * NOT_SAFE problem entirely so sleep proceeds despite nearby monsters.
     */
    private void onCanSleep(CanPlayerSleepEvent event) {
        if (event.getProblem() != Player.BedSleepingProblem.NOT_SAFE) return;
        if (!(event.getEntity() instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel sl)) return;

        if (SETTINGS.bool("highlight_blocking_mobs")) {
            Vec3 c = Vec3.atBottomCenterOf(event.getPos());
            AABB box = new AABB(c.x - 8.0, c.y - 5.0, c.z - 8.0, c.x + 8.0, c.y + 5.0, c.z + 8.0);
            for (Monster m : sl.getEntitiesOfClass(Monster.class, box, mob -> mob.isPreventingPlayerRest(sl, sp))) {
                MobHighlight.glowFor(sp, m, 200);
            }
        }
        if (SETTINGS.bool("sleep_ignore_monsters")) {
            event.setProblem(null);
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        var usleep = UltimateSleepCommands.register(event.getDispatcher());
        AFK_COMMANDS.registerAfkCommandIfAbsent(event.getDispatcher(), usleep.getChild("afk"));
    }
}
