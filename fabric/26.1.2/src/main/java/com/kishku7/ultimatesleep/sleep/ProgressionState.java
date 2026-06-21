package com.kishku7.ultimatesleep.sleep;

/**
 * Tiny static bridge so block-entity mixins (e.g. furnaces) know when a night-skip progression is
 * happening this tick and by how many ticks. Set by ServerLevelProgressionMixin during the skip
 * tick and cleared at its tail.
 */
public final class ProgressionState {

    private ProgressionState() {}

    public static volatile boolean active = false;
    public static volatile long ticks = 0;
}
