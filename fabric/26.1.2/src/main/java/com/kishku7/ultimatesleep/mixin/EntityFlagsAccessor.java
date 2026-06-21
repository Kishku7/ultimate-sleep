package com.kishku7.ultimatesleep.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes Entity.DATA_SHARED_FLAGS_ID so we can craft a per-viewer glow packet (FLAG_GLOWING). */
@Mixin(Entity.class)
public interface EntityFlagsAccessor {

    @Accessor("DATA_SHARED_FLAGS_ID")
    static EntityDataAccessor<Byte> usleep$sharedFlags() {
        throw new AssertionError();
    }
}
