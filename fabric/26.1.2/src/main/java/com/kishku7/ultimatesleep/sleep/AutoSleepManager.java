package com.kishku7.ultimatesleep.sleep;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.config.Settings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Auto-sleep: opted-in players are automatically put to bed at dusk.
 *
 * Opt-in is per-player (persisted by name) via /usleep auto; the server-wide feature toggle is
 * auto_sleep_enabled (default true). At the dusk edge (overworld transitions from bright to not
 * bright -- isBrightOutside) we make one pass: for each opted-in, awake player in the overworld
 * we look for a bed within vanilla reach and inject a sleep request (server-side startSleepInBed,
 * same path as right-clicking the bed). If no reachable bed is found, the player is told they
 * missed their sleep.
 *
 * TODO: home-bed-specific check (respawn pos) + Travelers' Backpack sleeping-bag path (soft-dep)
 * -- currently uses the nearest reachable bed. See FUNCTIONAL_SPEC.md section 9.
 */
public final class AutoSleepManager {

    private final Settings settings;
    private final Set<String> optedIn = new LinkedHashSet<>();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("ultimate_sleep_autosleep.json");
    private static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private boolean wasBright = true;

    public AutoSleepManager(Settings settings) {
        this.settings = settings;
    }

    public boolean isOptedIn(ServerPlayer p) {
        return optedIn.contains(p.getName().getString().toLowerCase());
    }

    /** Toggle a player's auto-sleep opt-in. @return resulting opt-in state. */
    public boolean toggle(ServerPlayer p) {
        String n = p.getName().getString().toLowerCase();
        boolean now;
        if (optedIn.contains(n)) { optedIn.remove(n); now = false; }
        else { optedIn.add(n); now = true; }
        save();
        return now;
    }

    public void tick(MinecraftServer server) {
        if (!settings.bool("auto_sleep_enabled")) { wasBright = true; return; }
        ServerLevel ow = server.overworld();
        if (ow == null) return;
        boolean bright = ow.isBrightOutside();
        if (wasBright && !bright) {
            duskPass(server, ow);
        }
        wasBright = bright;
    }

    private void duskPass(MinecraftServer server, ServerLevel ow) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() != ow) continue;
            if (!isOptedIn(p) || p.isSleeping()) continue;
            BlockPos bed = findReachableBed(ow, p);
            if (bed == null) {
                p.sendSystemMessage(Component.literal("[Ultimate Sleep] Auto-sleep: not near your bed."));
                continue;
            }
            p.startSleepInBed(bed).ifLeft(problem -> {
                Component m = problem.message();
                if (m != null) p.sendSystemMessage(m);
            });
        }
    }

    /** Scan a small box (vanilla bed reach) around the player for a bed block. */
    private BlockPos findReachableBed(ServerLevel ow, ServerPlayer p) {
        BlockPos base = p.blockPosition();
        for (int dy = 2; dy >= -2; dy--) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    if (ow.getBlockState(pos).getBlock() instanceof BedBlock) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    public void load() {
        try {
            if (Files.exists(file)) {
                List<String> names = GSON.fromJson(Files.readString(file),
                        new TypeToken<List<String>>() {}.getType());
                optedIn.clear();
                if (names != null) for (String n : names) optedIn.add(n.toLowerCase());
            }
        } catch (Exception ex) {
            UltimateSleep.LOGGER.warn("[UltimateSleep] failed to load auto-sleep list: " + ex.getMessage());
        }
    }

    public void save() {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(new ArrayList<>(optedIn)));
        } catch (Exception ex) {
            UltimateSleep.LOGGER.warn("[UltimateSleep] failed to save auto-sleep list: " + ex.getMessage());
        }
    }
}
