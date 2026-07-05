package com.kishku7.ultimatesleep;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/** Loader facade (Fabric). NeoForge provides its own Platform with the same signatures. */
public final class Platform {
    private Platform() {}
    public static Path configDir() { return FabricLoader.getInstance().getConfigDir(); }
    public static boolean isModLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
}
