package com.kishku7.ultimatesleep.compat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Era seam: every expression-level MC-API drift point in the shared code routes through here, so
 * business logic stays identical across all MC versions. This file is the PLAIN 26-shaped twin;
 * pre-26 cells get a cog-materialized version of this ONE file with per-era bodies
 * (_codegen/cog_sources/shared/compat/Era.java). Keep the two in sync -- check-sync.ps1 verifies.
 *
 * Era boundaries (ground truth from decompiled trees; see Temp/usleep-backport/era-boundaries.md):
 * 1.21.5 isDay->isBrightOutside + SPEED rename + RespawnConfig; 1.21.11 Identifier/gamerules/
 * Permissions/BedRule/record-BedSleepingProblem wave; 26 sendOverlayMessage + ServerClockManager.
 */
public final class Era {

    private Era() {}

    /** Namespaced id for this mod (Identifier at 1.21.11+, ResourceLocation before). */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("ultimate_sleep", path);
    }

    /** Daylight check: isBrightOutside (1.21.5+) / isDay (older). */
    public static boolean bright(Level level) {
        return level.isBrightOutside();
    }

    /** Action-bar (overlay) message: sendOverlayMessage (26+) / displayClientMessage(msg,true). */
    public static void overlay(ServerPlayer p, Component msg) {
        p.sendOverlayMessage(msg);
    }

    /** Speed effect instance: MobEffects.SPEED (1.21.5+) / MOVEMENT_SPEED (older). */
    public static MobEffectInstance speedBoost(int duration, int amplifier) {
        return new MobEffectInstance(MobEffects.SPEED, duration, amplifier);
    }

    /** Op tier 2 (gamemaster): permissions API (1.21.11+) / hasPermission(2). */
    public static boolean hasGamemaster(CommandSourceStack src) {
        return src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /** Op tier 3 (admin): permissions API (1.21.11+) / hasPermission(3). */
    public static boolean hasAdmin(CommandSourceStack src) {
        return src.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
    }

    /** The player's respawn (home bed) position, or null. Three accessor eras. */
    public static GlobalPos respawnGlobalPos(ServerPlayer p) {
        var rc = p.getRespawnConfig();
        if (rc == null || rc.respawnData() == null) return null;
        return rc.respawnData().globalPos();
    }

    /** BedSleepingProblem message: record accessor (1.21.11+) / enum getMessage() (older). */
    public static Component problemMessage(Player.BedSleepingProblem problem) {
        return problem.message();
    }

    // ---- ACCELERATE clock group. 26: day-night is driven by ServerClockManager RATE; pre-26 the
    // ---- lever is per-tick setDayTime stepping (accelStep), and setClockRate is a no-op.

    /** Day-ticks remaining until morning (>= 1). */
    public static long ticksToMorning(ServerLevel ow) {
        long r = 24000L - (ow.getOverworldClockTime() % 24000L);
        return r < 1 ? 1 : r;
    }

    /** 26: set the overworld clock rate. Pre-26: no-op (accelStep does the work). */
    public static void setClockRate(MinecraftServer server, ServerLevel ow, float rate) {
        ow.dimensionType().defaultClock().ifPresent(clock -> server.clockManager().setRate(clock, rate));
    }

    /** Per-tick acceleration step. 26: no-op (clock rate drives time). Pre-26: advance dayTime. */
    public static void accelStep(MinecraftServer server, ServerLevel ow, float rate) {
        // 26: nothing -- the clock rate set in setClockRate advances time.
    }
}
