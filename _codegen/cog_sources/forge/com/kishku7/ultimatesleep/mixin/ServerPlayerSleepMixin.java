package com.kishku7.ultimatesleep.mixin;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * FORGE twin of ServerPlayerSleepMixin (copied over the shared twin by cog-gen when
 * flavour=forge). Only the ignore_bed_too_far redirect survives here:
 *
 *  - bedInRange(BlockPos,Direction)Z: the INVOKE is UNTOUCHED by the Forge patch on every
 *    Forge version 47.3.0-61.1.0 (bytecode-verified in the FG6 recomp jars, 2026-07-05), and
 *    the descriptor is era-stable 1.20.1-1.21.11 -- so no cog blocks at all.
 *
 *  - The shared twin's sleep_anytime day-gate redirect (isDay/isBrightOutside/BedRule.canSleep)
 *    CANNOT exist on Forge: the patch replaces that call with
 *    ForgeEventFactory.onSleepingTimeCheck on every version. sleep_anytime is handled by the
 *    SleepingTimeCheckEvent listener in the Forge entrypoint instead (identical semantics:
 *    ALLOW bypasses the gate, DEFAULT preserves vanilla).
 *
 * Both toggles are admin settings (off by default); when off, vanilla behavior is preserved.
 * (highlight_blocking_mobs / sleep_ignore_monsters live in ForgeSleepMonstersMixin.)
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSleepMixin {

    @Shadow
    private boolean bedInRange(BlockPos pos, Direction direction) {
        throw new AssertionError("mixin shadow");
    }

    @Redirect(method = "startSleepInBed",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;bedInRange(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"))
    private boolean ultimateSleep$bedInRange(ServerPlayer self, BlockPos pos, Direction direction) {
        if (UltimateSleep.settings().bool("ignore_bed_too_far")) return true;
        return this.bedInRange(pos, direction);
    }
}
