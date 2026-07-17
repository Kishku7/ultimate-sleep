package com.kishku7.ultimatesleep.sleep;

import com.kishku7.ultimatesleep.compat.Era;

import com.kishku7.ultimatesleep.config.Settings;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Grants admin-selected rewards to players who slept through a skip.
 *
 * Mode-agnostic detection: we watch the overworld day/night edge (isBrightOutside going
 * dark -> bright = morning, which is exactly when a sleep-skip lands or natural dawn occurs) and
 * reward whoever was sleeping on the previous tick. Natural dawn with nobody sleeping rewards
 * nobody. Works for SIMPLE (vanilla or forced) and VOTE skips alike.
 *
 * Rewards (each an independent admin toggle): Regeneration, a golden carrot (dropped at the
 * player's feet if their inventory is full), and a movement-speed boost.
 *
 * NOTE: the speed reward uses the vanilla Speed effect (20% per level), so reward_speed_boost_percent
 * is mapped to the nearest effect level. An exact-percent attribute modifier is a possible refinement.
 */
public final class RewardManager {

    private final Settings settings;
    private boolean wasBright = true;
    private Set<UUID> lastSleepers = new HashSet<>();

    public RewardManager(Settings settings) {
        this.settings = settings;
    }

    public void tick(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        if (ow == null) return;

        Set<UUID> current = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isSpectator() && p.isSleeping() && p.level() == ow) current.add(p.getUUID());
        }

        boolean bright = Era.bright(ow);
        if (!wasBright && bright && anyRewardEnabled()) {
            for (UUID id : lastSleepers) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) grant(p);
            }
        }
        wasBright = bright;
        lastSleepers = current;
    }

    private boolean anyRewardEnabled() {
        return settings.bool("reward_regeneration")
                || settings.bool("reward_golden_carrot")
                || settings.bool("reward_speed_boost");
    }

    private void grant(ServerPlayer p) {
        if (settings.bool("reward_regeneration")) {
            int dur = Math.max(1, settings.integer("reward_regeneration_minutes")) * 60 * 20;
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, dur, 0), null);
        }
        if (settings.bool("reward_speed_boost")) {
            int dur = Math.max(1, settings.integer("reward_speed_boost_minutes")) * 60 * 20;
            int amp = Math.max(0, Math.round(settings.integer("reward_speed_boost_percent") / 20.0f) - 1);
            p.addEffect(Era.speedBoost(dur, amp), null);
        }
        if (settings.bool("reward_golden_carrot")) {
            ItemStack stack = new ItemStack(Items.GOLDEN_CARROT);
            if (!p.getInventory().add(stack)) {
                p.drop(stack, false);
            }
        }
    }
}
