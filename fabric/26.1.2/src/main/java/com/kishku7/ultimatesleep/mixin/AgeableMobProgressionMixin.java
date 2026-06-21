package com.kishku7.ultimatesleep.mixin;

import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.sleep.ProgressionState;
import net.minecraft.world.entity.AgeableMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * World progression -- animal husbandry. On a night-skip tick, advance each loaded animal's age
 * counter toward 0 by ticksSlept: babies (age &lt; 0) grow toward adulthood, and adult breeding
 * cooldowns (age &gt; 0) tick down. Applied once per animal on the skip tick (aiStep runs once).
 */
@Mixin(AgeableMob.class)
public abstract class AgeableMobProgressionMixin {

    @Inject(method = "aiStep()V", at = @At("HEAD"))
    private void ultimateSleep$progressAge(CallbackInfo ci) {
        if (!ProgressionState.active || !UltimateSleep.settings().bool("progress_animal_husbandry")) return;
        AgeableMob self = (AgeableMob) (Object) this;
        if (self.level().isClientSide()) return;
        long ticks = ProgressionState.ticks;
        int age = self.getAge();
        if (age < 0) {
            self.setAge((int) Math.min(0L, age + ticks));
        } else if (age > 0) {
            self.setAge((int) Math.max(0L, age - ticks));
        }
    }
}
