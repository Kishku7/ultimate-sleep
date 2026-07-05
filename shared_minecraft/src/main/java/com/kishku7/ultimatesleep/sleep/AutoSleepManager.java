package com.kishku7.ultimatesleep.sleep;

import com.kishku7.ultimatesleep.compat.Era;

import com.kishku7.ultimatesleep.Platform;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kishku7.ultimatesleep.UltimateSleep;
import com.kishku7.ultimatesleep.compat.TravelersBackpackCompat;
import com.kishku7.ultimatesleep.config.Settings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Auto-sleep: opted-in players are automatically put to bed at dusk.
 *
 * Opt-in is per-player (persisted by name) via /usleep auto; the server-wide feature toggle is
 * auto_sleep_enabled (default true). At the dusk edge (overworld bright -> not bright) we make one
 * pass: for each opted-in, awake overworld player we
 *   1. target their home bed (spawn bed) if it's a real bed within reach, else the nearest
 *      reachable bed, and inject a sleep request (same path as right-clicking the bed); or
 *   2. if no bed is reachable but they carry a Travelers' Backpack sleeping bag (soft-dep), place
 *      one in place and sleep there -- Ultimate Sleep removes that block when they wake; or
 *   3. tell them why they couldn't auto-sleep.
 */
public final class AutoSleepManager {

    private final Settings settings;
    private final Set<String> optedIn = new LinkedHashSet<>();
    private final Map<UUID, BlockPos[]> placedBags = new HashMap<>();
    private final Path file = Platform.configDir().resolve("ultimate_sleep_autosleep.json");
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
        cleanupPlacedBags(server);
        if (!settings.bool("auto_sleep_enabled")) { wasBright = true; return; }
        ServerLevel ow = server.overworld();
        if (ow == null) return;
        boolean bright = Era.bright(ow);
        if (wasBright && !bright) {
            duskPass(server, ow);
        }
        wasBright = bright;
    }

    private void duskPass(MinecraftServer server, ServerLevel ow) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() != ow) continue;
            if (!isOptedIn(p) || p.isSleeping()) continue;

            BlockPos bed = findBed(ow, p);
            if (bed != null) {
                p.startSleepInBed(bed).ifLeft(problem -> {
                    Component m = Era.problemMessage(problem);
                    if (m != null) p.sendSystemMessage(m);
                });
                continue;
            }

            if (TravelersBackpackCompat.isPresent() && TravelersBackpackCompat.hasSleepingBag(p)) {
                BlockPos[] placed = TravelersBackpackCompat.placeAndSleep(ow, p);
                if (placed != null) {
                    placedBags.put(p.getUUID(), placed);
                    continue;
                }
                p.sendSystemMessage(Component.literal(
                        "[Ultimate Sleep] Auto-sleep: no room to roll out your sleeping bag here."));
                continue;
            }

            if (TravelersBackpackCompat.isPresent()) {
                p.sendSystemMessage(Component.literal(
                        "[Ultimate Sleep] Auto-sleep: not near your bed, and you don't have a sleeping bag with you."));
            } else {
                p.sendSystemMessage(Component.literal("[Ultimate Sleep] Auto-sleep: not near your bed."));
            }
        }
    }

    /** Prefer the player's home (spawn) bed if it's a real bed within reach, else the nearest bed. */
    private BlockPos findBed(ServerLevel ow, ServerPlayer p) {
        BlockPos base = p.blockPosition();
        GlobalPos gp = Era.respawnGlobalPos(p);
        {
            if (gp != null && ow.dimension().equals(gp.dimension())) {
                BlockPos home = gp.pos();
                if (inReach(base, home) && ow.getBlockState(home).getBlock() instanceof BedBlock) {
                    return home;
                }
            }
        }
        return findReachableBed(ow, base);
    }

    private static boolean inReach(BlockPos base, BlockPos pos) {
        return Math.abs(pos.getX() - base.getX()) <= 3
                && Math.abs(pos.getY() - base.getY()) <= 2
                && Math.abs(pos.getZ() - base.getZ()) <= 3;
    }

    /** Scan a small box (vanilla bed reach) around the player for a bed block. */
    private BlockPos findReachableBed(ServerLevel ow, BlockPos base) {
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

    /** Once a player who slept in a placed sleeping bag wakes (or leaves), take the block back out. */
    private void cleanupPlacedBags(MinecraftServer server) {
        if (placedBags.isEmpty()) return;
        ServerLevel ow = server.overworld();
        if (ow == null) return;
        placedBags.entrySet().removeIf(e -> {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && p.isSleeping()) return false; // still snoozing -- leave it
            BlockPos[] pos = e.getValue();
            TravelersBackpackCompat.remove(ow, pos[0], pos[1]);
            return true;
        });
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
