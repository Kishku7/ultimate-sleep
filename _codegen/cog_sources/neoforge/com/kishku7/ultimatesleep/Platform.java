package com.kishku7.ultimatesleep;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * Loader facade (NeoForge). Fabric provides its own Platform with the same signatures.
 * No cog blocks: FMLPaths.CONFIGDIR and ModList.get().isLoaded are unchanged across
 * NeoForge 20.6-21.11 (and 26) -- verified against the cached universal jars.
 */
public final class Platform {
    private Platform() {}
    public static Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    public static boolean isModLoaded(String id) { return ModList.get().isLoaded(id); }
}
