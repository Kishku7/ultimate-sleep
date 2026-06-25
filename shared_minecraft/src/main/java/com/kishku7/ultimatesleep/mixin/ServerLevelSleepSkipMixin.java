package com.kishku7.ultimatesleep.mixin;

import com.kishku7.ultimatesleep.UltimateSleep;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mod-driven night-skip control (Dave's policy, 2026-06-21): the playersSleepingPercentage
 * gamerule is pinned to 101 and left there, so vanilla NEVER skips the night on its own (the
 * sleepers-needed count rounds to activePlayers+1, which is unreachable). The only thing that
 * advances the night is this mod, when its engine/vote logic explicitly asks for it.
 *
 * The vanilla skip block in ServerLevel.tick is gated on:
 *     sleepStatus.areEnoughSleeping(pct) && sleepStatus.areEnoughDeepSleeping(pct, players)
 * We redirect BOTH calls (overworld only) so the gate is driven purely by the engine's pending
 * flag. When the mod requests a skip, both return true for one tick and vanilla performs the
 * real work -- clockManager.moveToTimeMarker(WAKE_UP_FROM_SLEEP), wakeUpAllPlayers(), and the
 * weather reset (still subject to ServerLevelWeatherMixin / preserve_weather). When the mod is
 * NOT requesting a skip the gate is false, so vanilla does nothing regardless of how many
 * players are in bed. This keeps every skip path (SIMPLE, VOTE, AFK-excluded) routed through one
 * deterministic trigger and reuses all of vanilla's skip internals.
 *
 * We also suppress vanilla's own "x/y players sleeping" action-bar announcement, since the mod
 * now owns all sleep feedback (its own messages in SIMPLE / VOTE). This removes the confusing
 * double message and the stale vanilla count.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSleepSkipMixin {

    @Redirect(method = "tick(Ljava/util/function/BooleanSupplier;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/players/SleepStatus;areEnoughSleeping(I)Z"))
    private boolean ultimateSleep$enoughSleeping(SleepStatus status, int pct) {
        ServerLevel self = (ServerLevel) (Object) this;
        MinecraftServer server = self.getServer();
        if (server == null || self != server.overworld()) {
            return status.areEnoughSleeping(pct);
        }
        return UltimateSleep.engine().isSkipPending();
    }

    @Redirect(method = "tick(Ljava/util/function/BooleanSupplier;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/players/SleepStatus;areEnoughDeepSleeping(ILjava/util/List;)Z"))
    private boolean ultimateSleep$enoughDeep(SleepStatus status, int pct, List<ServerPlayer> players) {
        ServerLevel self = (ServerLevel) (Object) this;
        MinecraftServer server = self.getServer();
        if (server == null || self != server.overworld()) {
            return status.areEnoughDeepSleeping(pct, players);
        }
        boolean pending = UltimateSleep.engine().isSkipPending();
        if (pending) UltimateSleep.engine().consumeSkip();
        return pending;
    }

    @Inject(method = "announceSleepStatus()V", at = @At("HEAD"), cancellable = true)
    private void ultimateSleep$suppressVanillaAnnounce(CallbackInfo ci) {
        ci.cancel();
    }
}
