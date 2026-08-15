package com.kishku7.ultimatesleep.mixin;

import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.sleep.ProgressionState;
import net.minecraft.core.BlockPos;
//[[[cog
//import sys; sys.path.insert(0, codegen); import compat
//if compat.sl2(ver): cog.outl("import net.minecraft.server.level.ServerLevel;")
//else:               cog.outl("import net.minecraft.world.level.Level;")
//]]]
import net.minecraft.server.level.ServerLevel;
//[[[end]]]
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * progress_smelting: when a night-skip catch-up slice is active this tick, fast-forward this
 * furnace by running the real serverTick once per SIMULATED TICK IN THAT SLICE (re-entry guarded so
 * the replay runs the normal body), then cancel the outer call. Pre-1.3.0 this replayed the whole
 * night (ticksSlept, up to ~11k iterations per furnace) in the single skip tick, which is half of
 * why a sleep stalled the server; the slice bounds it. No smelting math is replicated -- we just replay vanilla's
 * own tick, so fuel use / recipe output / lit state stay exactly correct.
 * (serverTick's first parameter is ServerLevel from 1.21.2; Level before -- cog emits the era shape.)
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceProgressionMixin {

    @Unique private static boolean ultimateSleep$reentry = false;

    //[[[cog
    //lvl = compat.furnace_level_type(ver)
    //cog.outl('    @Inject(method = "%s",' % compat.furnace_descriptor(ver))
    //cog.outl('            at = @At("HEAD"), cancellable = true)')
    //cog.outl('    private static void ultimateSleep$progressSmelting(%s level, BlockPos pos, BlockState state,' % lvl)
    //cog.outl('                                                       AbstractFurnaceBlockEntity entity, CallbackInfo ci) {')
    //]]]
    @Inject(method = "serverTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private static void ultimateSleep$progressSmelting(ServerLevel level, BlockPos pos, BlockState state,
                                                       AbstractFurnaceBlockEntity entity, CallbackInfo ci) {
    //[[[end]]]
        if (ultimateSleep$reentry) return; // inside our own replay -> let the normal body run
        if (!ProgressionState.active || !UltimateSleep.settings().bool("progress_smelting")) return;
        long ticks = ProgressionState.ticksThisTick;
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
