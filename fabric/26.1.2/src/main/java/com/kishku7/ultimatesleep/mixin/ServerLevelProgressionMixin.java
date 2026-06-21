package com.kishku7.ultimatesleep.mixin;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * World progression -- crops/plants.
 *
 * At the vanilla night-skip (the moveToTimeMarker call in ServerLevel.tick), temporarily crank
 * RANDOM_TICK_SPEED to base * ticksSlept for that single tick, so a whole night's worth of random
 * ticks (crops, saplings/tree growth, bamboo, sugar cane/cactus, leaf decay) is applied at once;
 * the original value is restored at the tick tail.
 *
 * Fires on every jump-based skip (INSTANT, AFK-excluded, VOTE). ACCELERATE composition (boosting
 * random ticks while time-lapsing) is a TODO. Furnaces / animal husbandry / despawn are separate
 * (furnaces are the next progression piece).
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelProgressionMixin {

    @Unique private int ultimateSleep$savedRandomTickSpeed = 3;
    @Unique private boolean ultimateSleep$progressingThisTick = false;

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/clock/ServerClockManager;moveToTimeMarker(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;)Z"))
    private void ultimateSleep$onSkip(BooleanSupplier haveTime, CallbackInfo ci) {
        if (!UltimateSleep.settings().bool("world_progression_enabled")
                || !UltimateSleep.settings().bool("progress_crops")) {
            return;
        }
        ServerLevel self = (ServerLevel) (Object) this;
        long gameTime = self.getGameTime();
        long after = (gameTime + 24000L) - ((gameTime + 24000L) % 24000L);
        long ticksSlept = after - gameTime;
        if (ticksSlept <= 0) return;

        ultimateSleep$savedRandomTickSpeed = self.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
        int base = ultimateSleep$savedRandomTickSpeed > 0 ? ultimateSleep$savedRandomTickSpeed : 3;
        int boosted = (int) Math.min(Integer.MAX_VALUE, (long) base * ticksSlept);
        self.getGameRules().set(GameRules.RANDOM_TICK_SPEED, boosted, self.getServer());
        ultimateSleep$progressingThisTick = true;
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
    private void ultimateSleep$resetAfterSkip(BooleanSupplier haveTime, CallbackInfo ci) {
        if (!ultimateSleep$progressingThisTick) return;
        ServerLevel self = (ServerLevel) (Object) this;
        self.getGameRules().set(GameRules.RANDOM_TICK_SPEED, ultimateSleep$savedRandomTickSpeed, self.getServer());
        ultimateSleep$progressingThisTick = false;
    }
}
