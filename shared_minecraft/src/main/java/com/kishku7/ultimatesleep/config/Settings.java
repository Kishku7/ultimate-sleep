package com.kishku7.ultimatesleep.config;

import com.kishku7.ultimatesleep.Platform;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kishku7.ultimatesleep.UltimateSleep;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed, persisted settings registry for Ultimate Sleep.
 *
 * Enumerable + generic get/set so commands and (later) the GUI drive it uniformly:
 *   /usleep query                  -- dump all
 *   /usleep [admin] set k v        -- set one
 *
 * Persisted as JSON in the Fabric config dir (ultimate_sleep.json). Values are stored as
 * strings and re-parsed on load. See FUNCTIONAL_SPEC.md section 10 for the catalogue.
 */
public final class Settings {

    public enum Type { BOOL, INT, ENUM, STRING, PERCENT }

    public static final class Entry {
        public final String key;
        public final Type type;
        public final String description;
        public final List<String> allowed; // for ENUM; else empty
        private Object value;

        Entry(String key, Type type, Object def, String description, List<String> allowed) {
            this.key = key;
            this.type = type;
            this.value = def;
            this.description = description;
            this.allowed = allowed;
        }

        public Object get() { return value; }
        public boolean asBool() { return (Boolean) value; }
        public int asInt() { return (Integer) value; }
        public String asString() { return String.valueOf(value); }
        void set(Object v) { this.value = v; }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Path file = Platform.configDir().resolve("ultimate_sleep.json");
    private static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Settings() {
        // core
        reg("enabled", Type.BOOL, true, "Master switch for Ultimate Sleep behavior.");
        // engine
        regEnum("requirement_mode", "SIMPLE", "How a skip is triggered.", List.of("SIMPLE", "VOTE"));
        reg("required_sleep_percentage", Type.PERCENT, 50, "Percent of eligible players needed (SIMPLE).");
        reg("exclude_afk_from_requirement", Type.BOOL, true, "AFK players don't count toward the requirement.");
        reg("vote_duration_seconds", Type.INT, 30, "Voting window (VOTE).");
        regEnum("vote_pass_rule", "MAJORITY_NON_AFK", "Vote pass rule.",
                List.of("MAJORITY_CAST", "PERCENT_CAST", "MAJORITY_NON_AFK"));
        reg("vote_pass_percentage", Type.PERCENT, 50, "Percent of votes cast to pass (PERCENT_CAST).");
        regEnum("skip_mode", "INSTANT", "How the triggered skip is carried out.", List.of("INSTANT", "ACCELERATE"));
        regEnum("accelerate_speed", "QUICK", "How fast the night passes (ACCELERATE).",
                List.of("SLOW", "SLOWISH", "QUICK", "FAST"));
        reg("preserve_weather", Type.BOOL, false, "Keep rain/storms across a skip.");
        // accessibility
        reg("sleep_anytime", Type.BOOL, false, "Allow sleeping during the day.");
        reg("sleep_ignore_monsters", Type.BOOL, false, "Ignore the monsters-nearby block.");
        reg("ignore_bed_too_far", Type.BOOL, false, "Ignore the bed-too-far block.");
        reg("highlight_blocking_mobs", Type.BOOL, false, "Outline mobs preventing sleep.");
        // feedback
        reg("show_sleepers_in_chat", Type.BOOL, true, "Broadcast who is sleeping + progress.");
        reg("show_sleepers_on_vote_screen", Type.BOOL, true, "Show sleepers/tally on the vote popup.");
        reg("notify_wake", Type.BOOL, true, "Broadcast the morning/wake event.");
        // rewards
        reg("reward_regeneration", Type.BOOL, false, "Grant Regeneration on a successful sleep.");
        reg("reward_regeneration_minutes", Type.INT, 5, "Regeneration duration (minutes).");
        reg("reward_golden_carrot", Type.BOOL, false, "Give a golden carrot (drops if inventory full).");
        reg("reward_speed_boost", Type.BOOL, false, "Grant a movement-speed boost on a successful sleep.");
        reg("reward_speed_boost_percent", Type.PERCENT, 25, "Speed boost percent.");
        reg("reward_speed_boost_minutes", Type.INT, 5, "Speed boost duration (minutes).");
        // world progression
        reg("world_progression_enabled", Type.BOOL, false, "Master toggle for world progression on sleep.");
        reg("progress_crops", Type.BOOL, true, "Crops + plant/tree growth + leaf decay.");
        reg("progress_animal_husbandry", Type.BOOL, true, "Breeding cooldowns + baby growth.");
        reg("progress_smelting", Type.BOOL, true, "Furnaces / smokers / blast furnaces.");
        reg("progress_despawn_timers", Type.BOOL, false, "Item/entity despawn timers advance.");
        // auto-sleep
        reg("auto_sleep_enabled", Type.BOOL, true, "Allow players to opt into auto-sleep at dusk.");
        // afk
        reg("afk_threshold_seconds", Type.INT, 180, "Idle time before auto-AFK.");
        reg("provide_afk_command", Type.BOOL, true, "Register /afk alias when none exists.");
    }

    private void reg(String key, Type type, Object def, String desc) {
        entries.put(key, new Entry(key, type, def, desc, List.of()));
    }

    private void regEnum(String key, String def, String desc, List<String> allowed) {
        entries.put(key, new Entry(key, Type.ENUM, def, desc, allowed));
    }

    public Map<String, Entry> all() { return entries; }
    public Entry get(String key) { return entries.get(key); }
    public boolean bool(String key) { return get(key).asBool(); }
    public int integer(String key) { return get(key).asInt(); }
    public String string(String key) { return get(key).asString(); }

    /** Parse and apply a raw string value. @return null on success, or an error message. */
    public String setRaw(String key, String raw) {
        Entry e = entries.get(key);
        if (e == null) return "Unknown setting: " + key;
        try {
            switch (e.type) {
                case BOOL -> {
                    Boolean b = parseBool(raw);
                    if (b == null) return "Invalid boolean '" + raw + "' for " + key + " (use true/false, yes/no, 1/0, on/off)";
                    e.set(b);
                }
                case INT -> e.set(Integer.parseInt(raw));
                case PERCENT -> {
                    Integer v = parsePercent(raw);
                    if (v == null) return "Invalid percentage '" + raw + "' for " + key + " (use e.g. 50, 50%, or 1/2)";
                    e.set(v);
                }
                case STRING -> e.set(raw);
                case ENUM -> {
                    String up = raw.toUpperCase();
                    if (!e.allowed.contains(up)) {
                        return "Invalid value '" + raw + "' for " + key + " (allowed: " + e.allowed + ")";
                    }
                    e.set(up);
                }
            }
        } catch (Exception ex) {
            return "Invalid value '" + raw + "' for " + key + " (expected " + e.type + ")";
        }
        return null;
    }

    public void load() {
        try {
            if (Files.exists(file)) {
                JsonObject obj = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                for (Entry e : entries.values()) {
                    if (obj.has(e.key)) {
                        String err = setRaw(e.key, obj.get(e.key).getAsString());
                        if (err != null) UltimateSleep.LOGGER.warn("[UltimateSleep] config: " + err);
                    }
                }
            }
        } catch (Exception ex) {
            UltimateSleep.LOGGER.warn("[UltimateSleep] failed to load config: " + ex.getMessage());
        }
    }

    public void save() {
        try {
            JsonObject obj = new JsonObject();
            for (Entry e : entries.values()) obj.addProperty(e.key, e.asString());
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(obj));
        } catch (Exception ex) {
            UltimateSleep.LOGGER.warn("[UltimateSleep] failed to save config: " + ex.getMessage());
        }
    }

        /** Flexible percentage parse: "50", "50%", "1/2", or "0.5" -> 0..100; else null. */
    private static Integer parsePercent(String raw) {
        String s = raw.trim().toLowerCase();
        if (s.endsWith("%")) s = s.substring(0, s.length() - 1).trim();
        try {
            int val;
            if (s.contains("/")) {
                String[] parts = s.split("/");
                if (parts.length != 2) return null;
                double den = Double.parseDouble(parts[1].trim());
                if (den == 0) return null;
                val = (int) Math.round(Double.parseDouble(parts[0].trim()) / den * 100.0);
            } else if (s.contains(".")) {
                double d = Double.parseDouble(s);
                val = d <= 1.0 ? (int) Math.round(d * 100.0) : (int) Math.round(d);
            } else {
                val = Integer.parseInt(s);
            }
            return Math.max(0, Math.min(100, val));
        } catch (Exception e) {
            return null;
        }
    }
    /** Flexible boolean parse: true/yes/1/on -> true; false/no/0/off -> false; else null. Case-insensitive. */
    private static Boolean parseBool(String raw) {
        String s = raw.trim().toLowerCase();
        return switch (s) {
            case "1", "yes", "true", "on" -> Boolean.TRUE;
            case "0", "no", "false", "off" -> Boolean.FALSE;
            default -> null;
        };
    }
}
