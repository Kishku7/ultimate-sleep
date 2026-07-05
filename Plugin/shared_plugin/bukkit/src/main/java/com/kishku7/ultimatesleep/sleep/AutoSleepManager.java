package com.kishku7.ultimatesleep.sleep;

import com.kishku7.ultimatesleep.UltimateSleepPlugin;
import com.kishku7.ultimatesleep.config.PluginSettings;
import com.kishku7.ultimatesleep.platform.Platform;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Auto-sleep (Bukkit port of the mod's AutoSleepManager): opted-in players are automatically put
 * to bed at dusk.
 *
 * Opt-in is per-player (persisted by name under auto_sleep_players in config.yml) via
 * /usleep auto; the server-wide feature toggle is auto_sleep_enabled (default true). At the dusk
 * edge (bright -> not bright) one pass runs: for each opted-in, awake primary-world player we
 * scan the vanilla bed-reach box (x/z +-3, y +-2) for a bed block and inject sleep via
 * HumanEntity.sleep(location, false) -- the same checks a right-click gets (and so the same
 * PlayerBedEnterEvent accessibility overrides). If no bed is reachable they get a text notice.
 *
 * Differences from the mod (documented in Plugin/README.md): no home-bed preference (Bukkit's
 * respawn location does not distinguish the bed block reliably across lines; the nearest
 * reachable bed is used, which inside bed reach is almost always the home bed anyway) and no
 * Travelers' Backpack sleeping-bag path (that is a Fabric/NeoForge mod; it cannot exist on a
 * Paper server).
 */
public final class AutoSleepManager {

    private static final String CFG_KEY = "auto_sleep_players";

    private final UltimateSleepPlugin plugin;
    private final PluginSettings settings;
    private final Set<String> optedIn = new LinkedHashSet<>();
    private boolean wasBright = true;

    public AutoSleepManager(UltimateSleepPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public boolean isOptedIn(Player p) {
        return optedIn.contains(p.getName().toLowerCase(Locale.ROOT));
    }

    /** Toggle a player's auto-sleep opt-in. @return resulting opt-in state. */
    public boolean toggle(Player p) {
        String n = p.getName().toLowerCase(Locale.ROOT);
        boolean now;
        if (optedIn.contains(n)) { optedIn.remove(n); now = false; }
        else { optedIn.add(n); now = true; }
        save();
        return now;
    }

    public void tick(long now) {
        if (!settings.bool("enabled") || !settings.bool("auto_sleep_enabled")) {
            wasBright = true;
            return;
        }
        World w = plugin.primaryWorld();
        if (w == null) return;
        boolean bright = SleepEngine.bright(w);
        if (wasBright && !bright) {
            duskPass(w);
        }
        wasBright = bright;
    }

    private void duskPass(World w) {
        for (Player p : w.getPlayers()) {
            if (!isOptedIn(p) || p.isSleeping()) continue;
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            // The bed scan + sleep injection touches the player's surroundings, so on Folia it
            // must run on the player's region thread.
            Platform.runOnEntity(plugin, p, () -> tryAutoSleep(w, p));
        }
    }

    /** Scan the vanilla bed-reach box for a bed and try to sleep in it. */
    private void tryAutoSleep(World w, Player p) {
        Location base = p.getLocation();
        int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();
        boolean foundBed = false;
        for (int dy = 2; dy >= -2; dy--) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    Block b = w.getBlockAt(bx + dx, by + dy, bz + dz);
                    if (!Tag.BEDS.isTagged(b.getType())) continue;
                    foundBed = true;
                    if (p.sleep(b.getLocation(), false)) {
                        return; // asleep
                    }
                }
            }
        }
        if (foundBed) {
            p.sendMessage("[Ultimate Sleep] Auto-sleep: couldn't use the nearby bed right now.");
        } else {
            p.sendMessage("[Ultimate Sleep] Auto-sleep: not near your bed.");
        }
    }

    public void load() {
        optedIn.clear();
        for (String n : plugin.getConfig().getStringList(CFG_KEY)) {
            optedIn.add(n.toLowerCase(Locale.ROOT));
        }
    }

    private void save() {
        plugin.getConfig().set(CFG_KEY, new ArrayList<>(optedIn));
        plugin.saveConfig();
    }
}
