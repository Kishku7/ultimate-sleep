package com.kishku7.ultimatesleep.compat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
//[[[cog
//import sys; sys.path.insert(0, codegen); import compat
//cog.outl("import %s;" % compat.id_import(ver))
//]]]
import net.minecraft.resources.Identifier;
//[[[end]]]
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
//[[[cog
//if compat.renamed(ver): cog.outl("import net.minecraft.server.permissions.Permissions;")
//]]]
import net.minecraft.server.permissions.Permissions;
//[[[end]]]
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Era seam: every expression-level MC-API drift point in the shared code routes through here, so
 * business logic stays identical across all MC versions. This is the COG TWIN -- materialized per
 * cell by scripts/cog-gen.ps1; the plain 26-shaped twin lives in shared_minecraft (check-sync.ps1
 * verifies the two stay in step at ver=26.1). Boundaries: _codegen/compat.py (ground truth table
 * in Temp/usleep-backport/era-boundaries.md).
 */
public final class Era {

    private Era() {}

    /** Namespaced id for this mod (Identifier at 1.21.11+, ResourceLocation before). */
    //[[[cog
    //for ln in compat.era_id_method(ver): cog.outl(ln)
    //]]]
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("ultimate_sleep", path);
    }
    //[[[end]]]

    /** Daylight check: isBrightOutside (1.21.5+) / isDay (older). */
    public static boolean bright(Level level) {
        //[[[cog
        //cog.outl("        return %s;" % compat.bright_call(ver, "level"))
        //]]]
        return level.isBrightOutside();
        //[[[end]]]
    }

    /** Action-bar (overlay) message: sendOverlayMessage (26+) / displayClientMessage(msg,true). */
    public static void overlay(ServerPlayer p, Component msg) {
        //[[[cog
        //for ln in compat.era_overlay_body(ver): cog.outl(ln)
        //]]]
        p.sendOverlayMessage(msg);
        //[[[end]]]
    }

    /** Speed effect instance: MobEffects.SPEED (1.21.5+) / MOVEMENT_SPEED (older). */
    public static MobEffectInstance speedBoost(int duration, int amplifier) {
        //[[[cog
        //cog.outl("        return new MobEffectInstance(%s, duration, amplifier);" % compat.era_speed_field(ver))
        //]]]
        return new MobEffectInstance(MobEffects.SPEED, duration, amplifier);
        //[[[end]]]
    }

    /** Op tier 2 (gamemaster): permissions API (1.21.11+) / hasPermission(2). */
    public static boolean hasGamemaster(CommandSourceStack src) {
        //[[[cog
        //for ln in compat.era_perm_body(ver, 2): cog.outl(ln)
        //]]]
        return src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        //[[[end]]]
    }

    /** Op tier 3 (admin): permissions API (1.21.11+) / hasPermission(3). */
    public static boolean hasAdmin(CommandSourceStack src) {
        //[[[cog
        //for ln in compat.era_perm_body(ver, 3): cog.outl(ln)
        //]]]
        return src.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
        //[[[end]]]
    }

    /** The player's respawn (home bed) position, or null. Three accessor eras. */
    public static GlobalPos respawnGlobalPos(ServerPlayer p) {
        //[[[cog
        //for ln in compat.era_respawn_body(ver): cog.outl(ln)
        //]]]
        var rc = p.getRespawnConfig();
        if (rc == null || rc.respawnData() == null) return null;
        return rc.respawnData().globalPos();
        //[[[end]]]
    }

    /** BedSleepingProblem message: record accessor (1.21.11+) / enum getMessage() (older). */
    public static Component problemMessage(Player.BedSleepingProblem problem) {
        //[[[cog
        //for ln in compat.era_problem_body(ver): cog.outl(ln)
        //]]]
        return problem.message();
        //[[[end]]]
    }

    // ---- ACCELERATE clock group. 26: day-night is driven by ServerClockManager RATE; pre-26 the
    // ---- lever is per-tick setDayTime stepping (accelStep), and setClockRate is a no-op.

    /** Day-ticks remaining until morning (>= 1). */
    public static long ticksToMorning(ServerLevel ow) {
        //[[[cog
        //for ln in compat.era_ticks_body(ver): cog.outl(ln)
        //]]]
        long r = 24000L - (ow.getOverworldClockTime() % 24000L);
        return r < 1 ? 1 : r;
        //[[[end]]]
    }

    /** 26: set the overworld clock rate. Pre-26: no-op (accelStep does the work). */
    public static void setClockRate(MinecraftServer server, ServerLevel ow, float rate) {
        //[[[cog
        //for ln in compat.era_setrate_body(ver): cog.outl(ln)
        //]]]
        ow.dimensionType().defaultClock().ifPresent(clock -> server.clockManager().setRate(clock, rate));
        //[[[end]]]
    }

    /**
     * Absolute overworld DAY-CLOCK time (26: the ServerClockManager overworld clock; pre-26: the
     * level dayTime). This is the WEATHER-INDEPENDENT clock. bright() is NOT: it is derived from
     * sky light, which rain and (hard) thunder push below the daylight threshold at ANY time of
     * day, so bright() reads "night" all day long during a storm. Anything asking "has morning
     * arrived" must ask the clock, not the light.
     */
    public static long clockTime(ServerLevel ow) {
        //[[[cog
        //for ln in compat.era_clocktime_body(ver): cog.outl(ln)
        //]]]
        return ow.getOverworldClockTime();
        //[[[end]]]
    }

    /** True during the DAY half of the cycle (clock 0..11999). Weather-independent; see clockTime(). */
    public static boolean dayPhase(ServerLevel ow) {
        long t = clockTime(ow) % 24000L;
        if (t < 0L) t += 24000L;
        return t < 12000L;
    }

    /** Per-tick acceleration step. 26: no-op (clock rate drives time). Pre-26: advance dayTime. */
    public static void accelStep(MinecraftServer server, ServerLevel ow, float rate) {
        //[[[cog
        //for ln in compat.era_accelstep_body(ver): cog.outl(ln)
        //]]]
        // 26: nothing -- the clock rate set in setClockRate advances time.
        //[[[end]]]
    }
}
