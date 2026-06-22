package com.kishku7.ultimatesleep.mixin;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * ACCELERATE skip mode: when SleepEngine flags an active acceleration, fast-forward the overworld
 * by running tickTime() extra times each tick (accelerate_multiplier - 1 extra steps). This makes
 * the night pass as a time-lapse instead of an instant jump; SleepEngine wakes players at dawn.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelTimeMixin {

    @Shadow
    protected abstract void tickTime();

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
    private void ultimateSleep$accelerate(BooleanSupplier haveTime, CallbackInfo ci) {
        if (!UltimateSleep.engine().isAccelerating()) return;
        ServerLevel self = (ServerLevel) (Object) this;
        if (self.getServer() == null || self != self.getServer().overworld()) return;
        int extra = Math.max(1, UltimateSleep.engine().accelMultiplier()) - 1;
        for (int i = 0; i < extra; i++) {
            this.tickTime();
        }
    }
}
