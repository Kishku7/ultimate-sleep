package com.kishku7.ultimatesleep;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/** Loader facade (NeoForge). Fabric provides its own Platform with the same signatures. */
public final class Platform {
    private Platform() {}
    public static Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    public static boolean isModLoaded(String id) { return ModList.get().isLoaded(id); }
}
