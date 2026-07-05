package com.kishku7.ultimatesleep.mixin;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
//[[[cog
//import sys; sys.path.insert(0, codegen); import compat
//if compat.renamed(ver): cog.outl("import net.minecraft.world.attribute.BedRule;")
//]]]
import net.minecraft.world.attribute.BedRule;
//[[[end]]]
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Accessibility overrides applied inside {@code ServerPlayer.startSleepInBed}:
 *  - ignore_bed_too_far: bypass the TOO_FAR_AWAY distance check.
 *  - sleep_anytime: bypass the daytime gate. Per era that gate is BedRule.canSleep (1.21.11+),
 *    Level.isBrightOutside (1.21.5-1.21.8), or Level.isDay (older) -- the redirect target follows.
 *    NOTE (pre-1.21.11): the dimension gate (dimensionType().natural()) is deliberately NOT
 *    bypassed -- nether/end bed behavior stays vanilla there; on 1.21.11+ BedRule owns both and
 *    sleep_anytime bypasses the combined rule exactly as on 26.
 *
 * (highlight_blocking_mobs is handled in the sleep event, not here.)
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

    //[[[cog
    //if compat.renamed(ver):
    //    cog.outl('    @Redirect(method = "startSleepInBed",')
    //    cog.outl('            at = @At(value = "INVOKE",')
    //    cog.outl('                    target = "%s"))' % compat.sleep_gate_target(ver))
    //    cog.outl('    private boolean ultimateSleep$canSleep(BedRule rule, Level level) {')
    //    cog.outl('        if (UltimateSleep.settings().bool("sleep_anytime")) return true;')
    //    cog.outl('        return rule.canSleep(level);')
    //    cog.outl('    }')
    //else:
    //    # PRE-26 FABRIC: NO day-gate redirect. fabric-api entity-events redirects the same
    //    # Level.isDay/isBrightOutside call (equal priority -> injection failure, smoketest
    //    # 2026-07-05). sleep_anytime is handled by the ALLOW_SLEEP_TIME event in the entry.
    //    cog.outl('    // (day-gate leg intentionally absent pre-26: ALLOW_SLEEP_TIME event covers it)')
    //]]]
    @Redirect(method = "startSleepInBed",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/level/Level;)Z"))
    private boolean ultimateSleep$canSleep(BedRule rule, Level level) {
        if (UltimateSleep.settings().bool("sleep_anytime")) return true;
        return rule.canSleep(level);
    }
    //[[[end]]]
}
