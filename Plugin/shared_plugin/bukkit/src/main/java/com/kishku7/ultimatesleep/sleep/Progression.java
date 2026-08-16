package com.kishku7.ultimatesleep.sleep;

import com.kishku7.ultimatesleep.UltimateSleepPlugin;
import com.kishku7.ultimatesleep.config.PluginSettings;
import com.kishku7.ultimatesleep.platform.Platform;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.LivingEntity;

/**
 * World progression during a night skip -- the Bukkit-API subset of the mod's mixin-driven
 * progression (the functional spec section 7).
 *
 * What a plugin CAN do without NMS:
 *   - progress_crops: a bounded randomTickSpeed boost -- a skip of N ticks is spread over a
 *     window of progression_catchup_seconds real seconds, with the gamerule raised to
 *     max(worldRate, 3 * ceil(N / window)) for that window. Total delivered is ~3*N random
 *     ticks: what the night would have produced at VANILLA's rate, never scaled by the world's
 *     own randomTickSpeed (which used to stretch the window instead).
 *     Covers crops, saplings, bamboo, sugar cane/cactus, and leaf decay (all random-tick driven).
 *   - progress_animal_husbandry: Ageable age arithmetic -- baby growth (negative age counts up)
 *     and breeding cooldowns (positive age counts down) advance by the skipped ticks. Skipped on
 *     Folia (iterating another region's entities from the global tick is not allowed).
 *
 * What it CANNOT do (dropped -- see Plugin/README.md): furnace/smoker/blast-furnace fast-forward
 * and item/entity despawn timers both live in block-entity/entity internals with no Bukkit API.
 */
final class Progression {

    /**
     * Vanilla's default randomTickSpeed. The catch-up is computed from THIS, never from the
     * world's current rate: the growth a night represents is a vanilla constant, so a world
     * running randomTickSpeed 25 must not pay 8.3x the cost of a vanilla one for the same night.
     * Matches ProgressionState.VANILLA_RANDOM_TICK_SPEED on the mod side.
     */
    private static final int VANILLA_RANDOM_TICK_SPEED = 3;
    /** Fallback window when progression_catchup_seconds is 0 or nonsense (10s at 20 tps). */
    private static final int DEFAULT_BOOST_TICKS = 200;

    private final UltimateSleepPlugin plugin;
    private final PluginSettings settings;

    private World boostWorld = null;
    private int boostTicksLeft = 0;
    private int originalRandomTickSpeed = 3;
    private boolean foliaHusbandryNoteLogged = false;

    Progression(UltimateSleepPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    /**
     * Called at the start of every skip (INSTANT jump or ACCELERATE ramp) with the skipped ticks.
     *
     * GameRule.RANDOM_TICK_SPEED is deprecated-for-removal on the 1.21.11+/26.x Paper API in
     * favor of the registry lookup, which does not exist on the 1.20.x API line or plain Spigot;
     * the constant is the only cross-flavour call, hence the narrow suppression.
     */
    @SuppressWarnings({"deprecation", "removal"})
    void begin(World w, long skippedTicks) {
        if (!settings.bool("world_progression_enabled") || skippedTicks <= 0) return;

        if (settings.bool("progress_crops") && boostTicksLeft == 0) {
            Integer rts = w.getGameRuleValue(GameRule.RANDOM_TICK_SPEED);
            int base = rts == null ? VANILLA_RANDOM_TICK_SPEED : rts;
            if (base > 0) {
                // Window comes from the setting, not from the world's rate. Total random ticks
                // delivered is boosted * window ~= 3 * skippedTicks -- the vanilla amount -- however
                // high the world's own randomTickSpeed happens to be.
                int seconds = settings.integer("progression_catchup_seconds");
                int window = seconds > 0 ? seconds * 20 : DEFAULT_BOOST_TICKS;
                long slice = Math.max(1L, (skippedTicks + window - 1L) / window);
                long boosted = Math.max((long) base, (long) VANILLA_RANDOM_TICK_SPEED * slice);
                originalRandomTickSpeed = base;
                boostWorld = w;
                boostTicksLeft = window;
                w.setGameRule(GameRule.RANDOM_TICK_SPEED, (int) Math.min(Integer.MAX_VALUE, boosted));
                // Mirrors the mod's progression start/end pair: an admin who changes
                // progression_catchup_seconds can see it working.
                plugin.getLogger().info(String.format(
                        "progression start: catching up %d ticks over ~%ds (rate %d -> %d)",
                        skippedTicks, window / 20, base, (int) boosted));
            }
        }

        if (settings.bool("progress_animal_husbandry")) {
            if (Platform.isFolia()) {
                if (!foliaHusbandryNoteLogged) {
                    foliaHusbandryNoteLogged = true;
                    plugin.getLogger().info("progress_animal_husbandry is skipped on Folia "
                            + "(cross-region entity iteration is not permitted).");
                }
            } else {
                int step = (int) Math.min(skippedTicks, Integer.MAX_VALUE);
                for (LivingEntity e : w.getLivingEntities()) {
                    if (e instanceof Ageable a) {
                        int age = a.getAge();
                        if (age < 0) a.setAge(Math.min(0, age + step));       // baby growth
                        else if (age > 0) a.setAge(Math.max(0, age - step));  // breeding cooldown
                    }
                }
            }
        }
    }

    /** Called every tick: winds down the random-tick boost and restores the gamerule. */
    void tick() {
        if (boostTicksLeft > 0 && --boostTicksLeft == 0) {
            restore();
            plugin.getLogger().info("progression end: catch-up window complete, randomTickSpeed restored");
        }
    }

    /** Restore the boosted gamerule (also called from onDisable). GameRule: see begin() note. */
    @SuppressWarnings({"deprecation", "removal"})
    void restore() {
        if (boostWorld != null) {
            try {
                boostWorld.setGameRule(GameRule.RANDOM_TICK_SPEED, originalRandomTickSpeed);
            } catch (IllegalStateException e) {
                // Folia shutdown thread forbids setGameRule; the boost window is seconds long,
                // so a lost restore only matters if the server stops mid-burst.
                plugin.getLogger().info("randomTickSpeed restore skipped during shutdown ("
                        + e.getMessage() + ").");
            }
            boostWorld = null;
        }
        boostTicksLeft = 0;
    }
}
