package com.kishku7.ultimatesleep.mixin;

import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.sleep.ProgressionState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * World progression -- despawn timers. While a night-skip catch-up slice is active, advance
 * dropped-item age toward the 6000-tick lifetime by THAT SLICE, so litter that would have despawned
 * over the night does so. Items flagged immortal (age == -32768) are left alone. One slice per item
 * per tick, summing to exactly one night over the catch-up window.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityProgressionMixin {

    @Shadow
    private int age;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void ultimateSleep$progressDespawn(CallbackInfo ci) {
        if (!ProgressionState.active || !UltimateSleep.settings().bool("progress_despawn_timers")) return;
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.level().isClientSide() || this.age == -32768) return;
        long ticks = ProgressionState.ticksThisTick;
        this.age = (int) Math.min(6000L, this.age + ticks);
    }
}
