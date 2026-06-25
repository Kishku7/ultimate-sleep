package com.kishku7.ultimatesleep.mixin;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Accessibility overrides applied inside {@code ServerPlayer.startSleepInBed}:
 *  - ignore_bed_too_far: bypass the TOO_FAR_AWAY distance check.
 *  - sleep_anytime: bypass the BedRule time/dimension gate (canSleep).
 *
 * (highlight_blocking_mobs is handled in the ALLOW_NEARBY_MONSTERS event, not here.)
 * Both are admin toggles (off by default); when off, vanilla behavior is preserved.
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

    @Redirect(method = "startSleepInBed",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/level/Level;)Z"))
    private boolean ultimateSleep$canSleep(BedRule rule, Level level) {
        if (UltimateSleep.settings().bool("sleep_anytime")) return true;
        return rule.canSleep(level);
    }
}
