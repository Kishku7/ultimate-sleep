package com.kishku7.ultimatesleep.mixin;

import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.sleep.MobHighlight;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Predicate;

/**
 * FORGE-ONLY mixin: the accessibility monster overrides, mirroring Fabric's
 * EntitySleepEvents.ALLOW_NEARBY_MONSTERS and NeoForge's CanPlayerSleepEvent. Forge has NO
 * CanPlayerSleepEvent on any version 47-61 (jar-verified 2026-07-05), and its startSleepInBed
 * patch leaves the vanilla NOT_SAFE block intact -- so we redirect the single
 * getEntitiesOfClass(Monster.class, box, NOT_SAFE-predicate) call inside
 * ServerPlayer.startSleepInBed on every Forge cell:
 *
 *  - highlight_blocking_mobs: glow the blocking monsters for the sleeper only (200 ticks).
 *  - sleep_ignore_monsters: return an empty list, so vanilla never produces NOT_SAFE.
 *
 * Era drift handled by cog: the call receiver is Level through 1.21.5 and ServerLevel from
 * 1.21.8 (bytecode-verified in the FG6 recomp jars at 47/50/52/55 = Level, 58/60/61 =
 * ServerLevel). The predicate is captured as-is, so its arity drift (isPreventingPlayerRest
 * 2-arg from 1.21.2) never surfaces here.
 */
@Mixin(ServerPlayer.class)
public abstract class ForgeSleepMonstersMixin {

    //[[[cog
    //import sys; sys.path.insert(0, codegen); import compat_loaders
    //rcv = compat_loaders.forge_entities_receiver(ver)
    //cog.outl('    @Redirect(method = "startSleepInBed",')
    //cog.outl('            at = @At(value = "INVOKE",')
    //cog.outl('                    target = "%s"))' % compat_loaders.forge_entities_target(ver))
    //cog.outl('    private List<Monster> ultimateSleep$monsterGate(%s level, Class<Monster> cls, AABB box,' % rcv)
    //cog.outl('                                                    Predicate<? super Monster> pred) {')
    //]]]
    @Redirect(method = "startSleepInBed",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<Monster> ultimateSleep$monsterGate(ServerLevel level, Class<Monster> cls, AABB box,
                                                    Predicate<? super Monster> pred) {
    //[[[end]]]
        List<Monster> blockers = level.getEntitiesOfClass(cls, box, pred);
        if (!blockers.isEmpty()) {
            if (UltimateSleep.settings().bool("highlight_blocking_mobs")) {
                ServerPlayer sp = (ServerPlayer) (Object) this;
                for (Monster m : blockers) {
                    MobHighlight.glowFor(sp, m, 200);
                }
            }
            if (UltimateSleep.settings().bool("sleep_ignore_monsters")) return List.of();
        }
        return blockers;
    }
}
