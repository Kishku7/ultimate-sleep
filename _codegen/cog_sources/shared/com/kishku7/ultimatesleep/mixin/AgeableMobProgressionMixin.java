package com.kishku7.ultimatesleep.mixin;

import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.sleep.ProgressionState;
import net.minecraft.world.entity.AgeableMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * World progression -- animal husbandry. While a night-skip catch-up slice is active, advance
 * each loaded animal's age counter toward 0 by THAT SLICE: babies (age &lt; 0) grow toward
 * adulthood, and adult breeding cooldowns (age &gt; 0) tick down. One slice per animal per tick
 * (aiStep runs once a tick), summing to exactly one night over the catch-up window.
 */
@Mixin(AgeableMob.class)
public abstract class AgeableMobProgressionMixin {

    @Inject(method = "aiStep()V", at = @At("HEAD"))
    private void ultimateSleep$progressAge(CallbackInfo ci) {
        if (!ProgressionState.active || !UltimateSleep.settings().bool("progress_animal_husbandry")) return;
        AgeableMob self = (AgeableMob) (Object) this;
        if (self.level().isClientSide()) return;
        long ticks = ProgressionState.ticksThisTick;
        int age = self.getAge();
        if (age < 0) {
            self.setAge((int) Math.min(0L, age + ticks));
        } else if (age > 0) {
            self.setAge((int) Math.max(0L, age - ticks));
        }
    }
}
