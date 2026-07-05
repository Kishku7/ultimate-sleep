package com.kishku7.ultimatesleep.mixin;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
//[[[cog
//import sys; sys.path.insert(0, codegen); import compat
//if not compat.modern_net(ver): cog.outl("import org.spongepowered.asm.mixin.Shadow;")
//]]]
//[[[end]]]
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * preserve_weather: when a night is skipped, vanilla calls ServerLevel.resetWeatherCycle() to
 * clear rain/thunder. When the admin enables preserve_weather we redirect that single call (in
 * ServerLevel.tick) to a no-op, so storms survive the skip. Works for every skip path (vanilla
 * percentage, our AFK-excluded forced skip, and VOTE), since they all run through this block.
 * Era drift handled by cog: resetWeatherCycle() is PRIVATE on 1.20-1.20.4 (public by 1.20.5+;
 * ground truth era-boundaries.md, build-verified at 1.20.6), so the legacy era calls it through
 * a Shadow on this -- the redirected call site's receiver is always this ServerLevel, so the
 * two shapes are semantically identical. The redirect anchor descriptor is era-stable.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelWeatherMixin {

    //[[[cog
    //if not compat.modern_net(ver):
    //    cog.outl("    @Shadow")
    //    cog.outl("    protected abstract void resetWeatherCycle();")
    //    cog.outl("")
    //]]]
    //[[[end]]]
    @Redirect(method = "tick(Ljava/util/function/BooleanSupplier;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;resetWeatherCycle()V"))
    private void ultimateSleep$preserveWeather(ServerLevel self) {
        if (UltimateSleep.settings().bool("preserve_weather")) return; // keep the current weather
        //[[[cog
        //cog.outl("        %s.resetWeatherCycle();" % ("self" if compat.modern_net(ver) else "this"))
        //]]]
        self.resetWeatherCycle();
        //[[[end]]]
    }
}
