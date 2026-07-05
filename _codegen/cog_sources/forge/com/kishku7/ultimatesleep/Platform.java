package com.kishku7.ultimatesleep;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * Loader facade (Forge). Fabric/NeoForge provide their own Platform with the same signatures.
 * No cog blocks: FMLPaths.CONFIGDIR and ModList.get().isLoaded are unchanged across Forge
 * 47-61 -- verified against the cached fmlloader/fmlcore jars (fmlloader-1.21.11-61.1.0),
 * and bank-vault built 1.20.1-1.21.8 with exactly this pair.
 */
public final class Platform {
    private Platform() {}
    public static Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    public static boolean isModLoaded(String id) { return ModList.get().isLoaded(id); }
}
