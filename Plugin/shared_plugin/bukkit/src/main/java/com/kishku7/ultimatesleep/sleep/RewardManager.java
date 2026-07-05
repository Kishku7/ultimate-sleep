package com.kishku7.ultimatesleep.sleep;

import com.kishku7.ultimatesleep.UltimateSleepPlugin;
import com.kishku7.ultimatesleep.config.PluginSettings;
import com.kishku7.ultimatesleep.platform.Platform;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Grants admin-selected rewards to players who slept through a skip (Bukkit port of the mod's
 * RewardManager; behavior 1:1).
 *
 * Mode-agnostic detection: watch the primary world's day/night edge (dark -> bright = morning,
 * which is exactly when a sleep-skip lands or natural dawn occurs) and reward whoever was
 * sleeping on the previous tick. Natural dawn with nobody sleeping rewards nobody.
 *
 * Rewards (each an independent admin toggle): Regeneration, a golden carrot (dropped at the
 * player's feet if their inventory is full), and a movement-speed boost.
 *
 * NOTE: the speed reward uses the vanilla Speed effect (20% per level), so
 * reward_speed_boost_percent is mapped to the nearest effect level (same as the mod).
 */
public final class RewardManager {

    private final UltimateSleepPlugin plugin;
    private final PluginSettings settings;
    private boolean wasBright = true;
    private Set<UUID> lastSleepers = new HashSet<>();

    public RewardManager(UltimateSleepPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void tick(long now) {
        World w = plugin.primaryWorld();
        if (w == null) return;

        Set<UUID> current = new HashSet<>();
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() != GameMode.SPECTATOR && p.isSleeping()) {
                current.add(p.getUniqueId());
            }
        }

        boolean bright = SleepEngine.bright(w);
        if (!wasBright && bright && anyRewardEnabled()) {
            for (UUID id : lastSleepers) {
                Player p = plugin.getServer().getPlayer(id);
                if (p != null) {
                    Platform.runOnEntity(plugin, p, () -> grant(p));
                }
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

    private void grant(Player p) {
        if (settings.bool("reward_regeneration")) {
            int dur = Math.max(1, settings.integer("reward_regeneration_minutes")) * 60 * 20;
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, dur, 0));
        }
        if (settings.bool("reward_speed_boost")) {
            int dur = Math.max(1, settings.integer("reward_speed_boost_minutes")) * 60 * 20;
            int amp = Math.max(0, Math.round(settings.integer("reward_speed_boost_percent") / 20.0f) - 1);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, dur, amp));
        }
        if (settings.bool("reward_golden_carrot")) {
            ItemStack stack = new ItemStack(Material.GOLDEN_CARROT);
            for (ItemStack left : p.getInventory().addItem(stack).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
    }
}
