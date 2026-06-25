package com.kishku7.ultimatesleep.mixin;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * preserve_weather: when a night is skipped, vanilla calls ServerLevel.resetWeatherCycle() to
 * clear rain/thunder. When the admin enables preserve_weather we redirect that single call (in
 * ServerLevel.tick) to a no-op, so storms survive the skip. Works for every skip path (vanilla
 * percentage, our AFK-excluded forced skip, and VOTE), since they all run through this block.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelWeatherMixin {

    @Redirect(method = "tick(Ljava/util/function/BooleanSupplier;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;resetWeatherCycle()V"))
    private void ultimateSleep$preserveWeather(ServerLevel self) {
        if (UltimateSleep.settings().bool("preserve_weather")) return; // keep the current weather
        self.resetWeatherCycle();
    }
}
