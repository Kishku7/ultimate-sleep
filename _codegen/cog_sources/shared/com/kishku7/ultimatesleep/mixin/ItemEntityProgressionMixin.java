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
 * World progression -- despawn timers. On a night-skip tick, advance dropped-item age toward the
 * 6000-tick lifetime by ticksSlept, so litter that would have despawned over the night does so.
 * Items flagged immortal (age == -32768) are left alone. Applied once per item on the skip tick.
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
        long ticks = ProgressionState.ticks;
        this.age = (int) Math.min(6000L, this.age + ticks);
    }
}
