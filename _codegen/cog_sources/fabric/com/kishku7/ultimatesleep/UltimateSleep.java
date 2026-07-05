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
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
//[[[cog
//import sys; sys.path.insert(0, codegen); import compat
//if compat.is26(ver): cog.outl("import net.fabricmc.fabric.api.util.EventResult;")
//else:                cog.outl("import net.minecraft.world.InteractionResult;")
//]]]
import net.fabricmc.fabric.api.util.EventResult;
//[[[end]]]
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ultimate Sleep -- common (server + integrated-server) entrypoint (Fabric). Wires settings, the
 * permission roster, all per-tick managers, the accessibility sleep-event overrides, the /usleep
 * command tree, and the conditional /afk redirect alias. Era drift handled by cog: the fabric-api
 * sleep-event result type (EventResult at 26 vs InteractionResult before) and the
 * isPreventingPlayerRest arity (2-arg from 1.21.2).
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

        UltimateSleepNet.registerTypes();
        UltimateSleepNet.registerServer();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            AFK.tick(server);
            ENGINE.tick(server);
            AUTO.tick(server);
            VOTE.tick(server);
            REWARDS.tick(server);
            MobHighlight.tick(server);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(ENGINE::applyConfig);

        // Accessibility: sleep despite nearby monsters; optionally glow the blockers for the sleeper only.
        //[[[cog
        //pred = "mob.isPreventingPlayerRest(sl, sp)" if compat.sl2(ver) else "mob.isPreventingPlayerRest(sp)"
        //allow = "EventResult.ALLOW" if compat.is26(ver) else "InteractionResult.SUCCESS"
        //passr = "EventResult.PASS" if compat.is26(ver) else "InteractionResult.PASS"
        //cog.outl("        EntitySleepEvents.ALLOW_NEARBY_MONSTERS.register((entity, sleepingPos, vanillaResult) -> {")
        //cog.outl("            if (SETTINGS.bool(\"highlight_blocking_mobs\")")
        //cog.outl("                    && entity instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) {")
        //cog.outl("                Vec3 c = Vec3.atBottomCenterOf(sleepingPos);")
        //cog.outl("                AABB box = new AABB(c.x - 8.0, c.y - 5.0, c.z - 8.0, c.x + 8.0, c.y + 5.0, c.z + 8.0);")
        //cog.outl("                for (Monster m : sl.getEntitiesOfClass(Monster.class, box, mob -> %s)) {" % pred)
        //cog.outl("                    MobHighlight.glowFor(sp, m, 200);")
        //cog.outl("                }")
        //cog.outl("            }")
        //cog.outl("            if (SETTINGS.bool(\"sleep_ignore_monsters\")) return %s;" % allow)
        //cog.outl("            return %s;" % passr)
        //cog.outl("        });")
        //]]]
        EntitySleepEvents.ALLOW_NEARBY_MONSTERS.register((entity, sleepingPos, vanillaResult) -> {
            if (SETTINGS.bool("highlight_blocking_mobs")
                    && entity instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) {
                Vec3 c = Vec3.atBottomCenterOf(sleepingPos);
                AABB box = new AABB(c.x - 8.0, c.y - 5.0, c.z - 8.0, c.x + 8.0, c.y + 5.0, c.z + 8.0);
                for (Monster m : sl.getEntitiesOfClass(Monster.class, box, mob -> mob.isPreventingPlayerRest(sl, sp))) {
                    MobHighlight.glowFor(sp, m, 200);
                }
            }
            if (SETTINGS.bool("sleep_ignore_monsters")) return EventResult.ALLOW;
            return EventResult.PASS;
        });
        //[[[end]]]

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var usleep = UltimateSleepCommands.register(dispatcher);
            AFK_COMMANDS.registerAfkCommandIfAbsent(dispatcher, usleep.getChild("afk"));
        });
    }
}
