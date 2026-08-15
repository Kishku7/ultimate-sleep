package com.kishku7.ultimatesleep.sleep;

/**
 * Tiny static bridge telling the world-progression consumers -- crops (via the random-tick boost),
 * furnaces, ageable mobs and item despawn -- how much of a skipped night to apply RIGHT NOW.
 *
 * CONTRACT (CHANGED IN 1.3.0 -- READ THIS BEFORE ADDING A CONSUMER):
 *   active        -- a catch-up slice is being applied during THIS tick.
 *   ticksThisTick -- how many SIMULATED ticks this tick's slice is worth. This is NOT the length of
 *                    the night. Before 1.3.0 the field was named `ticks` and did mean the whole
 *                    night, because the entire catch-up happened inside the single tick that
 *                    performed the skip. It is now one slice of the night, and `active` stays true
 *                    across many consecutive ticks, so a consumer that treats this value as "ticks
 *                    slept" would apply a WHOLE NIGHT ON EVERY TICK of the catch-up window. The
 *                    rename is deliberate: it breaks any such consumer at compile time.
 *
 * Written only by ServerLevelProgressionMixin, on the server thread, between the head and tail of
 * one ServerLevel.tick -- so consumers only ever observe it during the tick of the level that is
 * catching up (the overworld), never from another dimension's tick.
 */
public final class ProgressionState {

    private ProgressionState() {}

    public static volatile boolean active = false;
    public static volatile long ticksThisTick = 0;

    /**
     * Vanilla's default randomTickSpeed. The crop catch-up boost is computed from THIS constant and
     * never from the world's current gamerule value: the amount of growth a night represents is a
     * vanilla constant, so the cost of skipping a night must not scale with a setting that has
     * nothing to do with sleep. A world running randomTickSpeed 25 used to pay 8.3x the cost of a
     * vanilla world for the same night.
     */
    public static final int VANILLA_RANDOM_TICK_SPEED = 3;

    /** Drop any in-flight slice. Called on server start so a crash mid-catch-up cannot leak state
     *  into the next world loaded by the same JVM (singleplayer keeps these statics alive). */
    public static void reset() {
        active = false;
        ticksThisTick = 0;
    }
}
