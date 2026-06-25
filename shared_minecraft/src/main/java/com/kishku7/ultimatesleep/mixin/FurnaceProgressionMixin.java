package com.kishku7.ultimatesleep.mixin;

import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.sleep.ProgressionState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * progress_smelting: when a night-skip progression is active this tick, fast-forward this furnace
 * by running the real serverTick ticksSlept times (re-entry guarded so the replay runs the normal
 * body), then cancel the outer call. No smelting math is replicated -- we just replay vanilla's
 * own tick, so fuel use / recipe output / lit state stay exactly correct.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceProgressionMixin {

    @Unique private static boolean ultimateSleep$reentry = false;

    @Inject(method = "serverTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private static void ultimateSleep$progressSmelting(ServerLevel level, BlockPos pos, BlockState state,
                                                       AbstractFurnaceBlockEntity entity, CallbackInfo ci) {
        if (ultimateSleep$reentry) return; // inside our own replay -> let the normal body run
        if (!ProgressionState.active || !UltimateSleep.settings().bool("progress_smelting")) return;
        long ticks = ProgressionState.ticks;
        if (ticks <= 0) return;

        ultimateSleep$reentry = true;
        try {
            for (long i = 0; i < ticks; i++) {
                AbstractFurnaceBlockEntity.serverTick(level, pos, level.getBlockState(pos), entity);
            }
        } finally {
            ultimateSleep$reentry = false;
        }
        ci.cancel();
    }
}
