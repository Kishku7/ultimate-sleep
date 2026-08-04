package com.kishku7.ultimatesleep.mixin;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * sleep_anytime, ONGOING leg (mod_support issue #10).
 *
 * Vanilla gates sleeping TWICE, not once:
 *   1. ENTRY  -- inside ServerPlayer.startSleepInBed. That is the leg ServerPlayerSleepMixin /
 *      the loader sleep events already cover.
 *   2. ONGOING -- inside Player.tick, EVERY tick while sleeping: if the day-gate says you may not
 *      sleep here right now, vanilla calls stopSleepInBed(false, true) and ejects you.
 *
 * Only leg 1 was ever bypassed, so with sleep_anytime on you passed the entry check, entered the
 * bed, and were thrown straight back out on the next tick -- and because you never stayed asleep
 * the skip never triggered and nothing progressed. Reported on Fabric 26.1.1; the same hole exists
 * on NeoForge (CanPlayerSleepEvent covers only startSleepInBed).
 *
 * The gate EXPRESSION drifts hard across eras -- Level.isDay (1.20-1.21.4),
 * Level.isBrightOutside (1.21.5-1.21.8), BedRule.canSleep via environmentAttributes
 * (1.21.11-26.2), AbstractBedBlock.getBedRule().canSleep (26.3+). The stopSleepInBed(ZZ)V CALL it
 * guards does not drift: identical descriptor, one call site inside tick, on every version
 * 1.20 -> 26.3. So we redirect the CONSEQUENCE, not the condition -- no cog gate needed, and no
 * collision with fabric-api's ALLOW_SLEEP_TIME redirect (which targets the condition and would
 * fail at equal priority; see the 2026-07-05 smoketest note in ServerPlayerSleepMixin).
 *
 * Dawn wake-up is unaffected: that runs through ServerLevel.wakeUpAllPlayers, not this call site.
 */
@Mixin(Player.class)
public abstract class PlayerSleepTickMixin {

    @Redirect(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;stopSleepInBed(ZZ)V"))
    private void ultimateSleep$keepSleeping(Player self, boolean forcefulWakeUp, boolean updateLevelList) {
        if (UltimateSleep.settings().bool("sleep_anytime")) return; // stay in bed
        self.stopSleepInBed(forcefulWakeUp, updateLevelList);
    }
}
