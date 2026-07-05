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
 * At the vanilla night-skip (26: the moveToTimeMarker call; pre-26: the setDayTime call, both in
 * ServerLevel.tick's sleep block) we publish a ProgressionState (active + ticksSlept) for that
 * tick so block-entity mixins (furnaces) can fast-forward, and -- if progress_crops -- temporarily
 * crank the random-tick-speed gamerule to base*ticksSlept so a whole night of random ticks is
 * applied at once. Everything is restored at the tick tail.
 *
 * Fires on every jump-based skip (INSTANT / AFK-excluded / VOTE).
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelProgressionMixin {

    @Unique private int ultimateSleep$savedRandomTickSpeed = 3;
    @Unique private boolean ultimateSleep$cropsBoosted = false;
    @Unique private boolean ultimateSleep$active = false;

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
        if (ticksSlept <= 0) return;

        ProgressionState.active = true;
        ProgressionState.ticks = ticksSlept;
        ultimateSleep$active = true;

        if (UltimateSleep.settings().bool("progress_crops")) {
            //[[[cog
            //cog.outl("            ultimateSleep$savedRandomTickSpeed = %s;" % compat.rndtick_get(ver))
            //]]]
            ultimateSleep$savedRandomTickSpeed = self.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
            //[[[end]]]
            int base = ultimateSleep$savedRandomTickSpeed > 0 ? ultimateSleep$savedRandomTickSpeed : 3;
            int boosted = (int) Math.min(Integer.MAX_VALUE, (long) base * ticksSlept);
            //[[[cog
            //cog.outl("            " + compat.rndtick_set(ver, "boosted"))
            //]]]
            self.getGameRules().set(GameRules.RANDOM_TICK_SPEED, boosted, self.getServer());
            //[[[end]]]
            ultimateSleep$cropsBoosted = true;
        }
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
    private void ultimateSleep$resetAfterSkip(BooleanSupplier haveTime, CallbackInfo ci) {
        if (!ultimateSleep$active) return;
        ProgressionState.active = false;
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
