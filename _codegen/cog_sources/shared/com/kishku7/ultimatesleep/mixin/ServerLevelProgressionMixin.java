package com.kishku7.ultimatesleep.mixin;

import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.sleep.ProgressionState;
import net.minecraft.server.level.ServerLevel;
//[[[cog
//import sys; sys.path.insert(0, codegen); import compat
//cog.outl(compat.gamerules_import(ver))
//]]]
import net.minecraft.world.level.gamerules.GameRules;
//[[[end]]]
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * World progression driver.
 *
 * At the vanilla night-skip (the universal wakeUpAllPlayers anchor in ServerLevel.tick's sleep
 * block) we QUEUE the night on this level, then pay it off over the following ticks: each tick a
 * slice is published to ProgressionState so the block-entity / entity mixins fast-forward by that
 * slice, and -- if progress_crops -- the random-tick-speed gamerule is raised for the body of that
 * one tick so the slice's worth of random ticks lands. Everything is restored at the tick tail.
 *
 * WHY IT IS SLICED (1.3.0). This used to happen entirely inside the skip tick: randomTickSpeed was
 * set to base * ticksSlept (33,000 at the vanilla base) and FurnaceProgressionMixin replayed a
 * furnace's serverTick once per slept tick. One tick therefore carried a whole night of work for
 * every loaded section and every furnace, which stalled the server thread for 2-3 seconds on every
 * sleep -- vanilla logged it as "Can't keep up! Is the server overloaded?". The total work is
 * unchanged; it is now spread over progression_catchup_seconds of real time so no single tick
 * carries more than its share.
 *
 * WHY THE BASE IS PINNED (1.3.0). The boost multiplied the world's CURRENT randomTickSpeed, so a
 * world running randomTickSpeed 25 paid 8.3x the cost of a vanilla world for the same night. A
 * night is a night: the boost is computed from ProgressionState.VANILLA_RANDOM_TICK_SPEED. The
 * world's own rate is never lowered while catching up -- the applied value is max(saved, 3*slice).
 *
 * SCOPE. remaining/slice are per-ServerLevel (@Unique) and the static flags are set and cleared
 * inside one level's tick, so only the level that actually skipped catches up, and no other
 * dimension's entities ever observe an active slice. Fires on every jump-based skip (INSTANT /
 * AFK-excluded / VOTE) and on the ACCELERATE hand-off, which finishes through the vanilla skip.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelProgressionMixin {

    @Unique private int ultimateSleep$savedRandomTickSpeed = 3;
    @Unique private boolean ultimateSleep$cropsBoosted = false;
    @Unique private boolean ultimateSleep$active = false;
    /** Simulated ticks still owed on this level. */
    @Unique private long ultimateSleep$remaining = 0L;
    /** Simulated ticks to apply per real tick; 0 means "all at once" (seconds <= 0). */
    @Unique private long ultimateSleep$slice = 0L;
    /** Bookkeeping for the start/end log pair: total queued, and the gameTime the run began. */
    @Unique private long ultimateSleep$runTotal = 0L;
    @Unique private long ultimateSleep$runStartTick = 0L;

    /**
     * Pay off one slice. HEAD of the level tick, so the raised gamerule covers this tick's chunk
     * ticking and the published slice covers this tick's entity + block-entity ticking.
     */
    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void ultimateSleep$pumpProgression(BooleanSupplier haveTime, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        // A previous tick threw before its TAIL ran: unwind before starting a new slice so the
        // gamerule can never stay raised across ticks.
        if (ultimateSleep$active) ultimateSleep$endSlice();

        if (ultimateSleep$remaining <= 0L) return;
        if (!UltimateSleep.settings().bool("world_progression_enabled")) {
            ultimateSleep$remaining = 0L;
            return;
        }

        long slice = ultimateSleep$slice > 0L
                ? Math.min(ultimateSleep$slice, ultimateSleep$remaining)
                : ultimateSleep$remaining;
        // Decrement up front: a slice that dies to an exception mid-tick is lost, never retried
        // forever.
        ultimateSleep$remaining -= slice;

        ProgressionState.active = true;
        ProgressionState.ticksThisTick = slice;
        ultimateSleep$active = true;

        if (ultimateSleep$remaining <= 0L && ultimateSleep$runTotal > 0L) {
            // Matches the ACCELERATE start/end pair the engine already logs, and gives an admin who
            // changes progression_catchup_seconds a way to SEE it working. It is also the only
            // external observable of the catch-up window: the random-tick boost is applied and
            // restored inside a single tick body, so nothing outside the tick can ever sample it.
            long elapsed = self.getGameTime() - ultimateSleep$runStartTick + 1L;
            UltimateSleep.LOGGER.info(String.format(
                    "[UltimateSleep] progression end: applied %d ticks over %d server ticks (%.1fs)",
                    ultimateSleep$runTotal, elapsed, elapsed / 20.0));
            ultimateSleep$runTotal = 0L;
        }

        if (UltimateSleep.settings().bool("progress_crops")) {
            //[[[cog
            //cog.outl("            ultimateSleep$savedRandomTickSpeed = %s;" % compat.rndtick_get(ver))
            //]]]
            ultimateSleep$savedRandomTickSpeed = self.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
            //[[[end]]]
            long boosted = Math.max((long) ultimateSleep$savedRandomTickSpeed,
                    (long) ProgressionState.VANILLA_RANDOM_TICK_SPEED * slice);
            int applied = (int) Math.min(Integer.MAX_VALUE, boosted);
            //[[[cog
            //cog.outl("            " + compat.rndtick_set(ver, "applied"))
            //]]]
            self.getGameRules().set(GameRules.RANDOM_TICK_SPEED, applied, self.getServer());
            //[[[end]]]
            ultimateSleep$cropsBoosted = true;
        }
    }

    /**
     * Queue the night. The anchor is vanilla's wakeUpAllPlayers call, which survives every loader's
     * patching of the sleep block on every supported version (see compat.progression_anchor). The
     * ticksSlept math reads gameTime, which the skip never touches, so it is correct whether the
     * anchor fires before or after the clock jump.
     */
    //[[[cog
    //cog.outl('    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V",')
    //cog.outl('            at = @At(value = "INVOKE",')
    //cog.outl('                    target = "%s"))' % compat.progression_anchor(ver))
    //]]]
    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;wakeUpAllPlayers()V"))
    //[[[end]]]
    private void ultimateSleep$onSkip(BooleanSupplier haveTime, CallbackInfo ci) {
        if (!UltimateSleep.settings().bool("world_progression_enabled")) return;
        ServerLevel self = (ServerLevel) (Object) this;
        long gameTime = self.getGameTime();
        long after = (gameTime + 24000L) - ((gameTime + 24000L) % 24000L);
        long ticksSlept = after - gameTime;
        if (ticksSlept <= 0L) return;

        boolean fresh = ultimateSleep$remaining <= 0L;
        ultimateSleep$remaining += ticksSlept;

        // How long the world should take to catch up, in real seconds. 0 (or a nonsense value)
        // means "all in one tick" -- the pre-1.3.0 behaviour, kept as an opt-in.
        int seconds = UltimateSleep.settings().integer("progression_catchup_seconds");
        if (fresh) {
            ultimateSleep$runTotal = ultimateSleep$remaining;
            ultimateSleep$runStartTick = gameTime;
            UltimateSleep.LOGGER.info(String.format(
                    "[UltimateSleep] progression start: catching up %d ticks over ~%ds",
                    ultimateSleep$remaining, Math.max(seconds, 0)));
        } else {
            ultimateSleep$runTotal += ticksSlept;
        }
        if (seconds <= 0) {
            ultimateSleep$slice = 0L; // 0 = unlimited slice
        } else {
            long realTicks = (long) seconds * 20L;
            ultimateSleep$slice = Math.max(1L, (ultimateSleep$remaining + realTicks - 1L) / realTicks);
        }
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
    private void ultimateSleep$resetAfterSlice(BooleanSupplier haveTime, CallbackInfo ci) {
        ultimateSleep$endSlice();
    }

    @Unique
    private void ultimateSleep$endSlice() {
        if (!ultimateSleep$active) return;
        ProgressionState.active = false;
        ProgressionState.ticksThisTick = 0L;
        if (ultimateSleep$cropsBoosted) {
            ServerLevel self = (ServerLevel) (Object) this;
            //[[[cog
            //cog.outl("            " + compat.rndtick_set(ver, "ultimateSleep$savedRandomTickSpeed"))
            //]]]
            self.getGameRules().set(GameRules.RANDOM_TICK_SPEED, ultimateSleep$savedRandomTickSpeed, self.getServer());
            //[[[end]]]
            ultimateSleep$cropsBoosted = false;
        }
        ultimateSleep$active = false;
    }
}
