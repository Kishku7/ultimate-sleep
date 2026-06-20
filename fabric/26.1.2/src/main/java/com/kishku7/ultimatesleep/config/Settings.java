package com.kishku7.ultimatesleep.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime settings for Ultimate Sleep.
 *
 * Settings are held in a typed registry so the admin command -- and later the
 * client admin panel -- can enumerate, query, and set them generically:
 *   /usleep admin query
 *   /usleep admin set &lt;key&gt; &lt;value&gt;
 *
 * The concrete feature set is still being decided (see ../../../../research for
 * the Modrinth sleep-mod feature survey). The entries below are an initial,
 * representative skeleton and are expected to grow as features are locked in.
 *
 * NOTE: persistence (load/save to a config file) is a TODO -- values are
 * currently in-memory only and reset on restart.
 */
public final class Settings {

    public enum Type { BOOL, INT }

    public static final class Entry {
        public final String key;
        public final Type type;
        public final String description;
        private Object value;

        Entry(String key, Type type, Object def, String description) {
            this.key = key;
            this.type = type;
            this.value = def;
            this.description = description;
        }

        public Object get() { return value; }
        public boolean asBool() { return (Boolean) value; }
        public int asInt() { return (Integer) value; }
        void set(Object v) { this.value = v; }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public Settings() {
        register("enabled", Type.BOOL, true,
                "Master enable switch for Ultimate Sleep behavior.");
        register("required_sleep_percentage", Type.INT, 50,
                "Percent of eligible players that must sleep to skip the night.");
        register("exclude_afk_from_requirement", Type.BOOL, true,
                "AFK players do not count toward the players needed to skip the night.");
        register("afk_threshold_seconds", Type.INT, 180,
                "Seconds of no movement or look change before a player is marked AFK.");
        register("provide_afk_command", Type.BOOL, true,
                "Register our own /afk command when no other mod already provides one.");
    }

    private void register(String key, Type type, Object def, String desc) {
        entries.put(key, new Entry(key, type, def, desc));
    }

    public Map<String, Entry> all() { return entries; }

    public Entry get(String key) { return entries.get(key); }

    public boolean bool(String key) { return get(key).asBool(); }

    public int integer(String key) { return get(key).asInt(); }

    /**
     * Parse and apply a raw string value to a setting.
     * @return null on success, or a human-readable error message.
     */
    public String setRaw(String key, String raw) {
        Entry e = entries.get(key);
        if (e == null) return "Unknown setting: " + key;
        try {
            switch (e.type) {
                case BOOL -> e.set(Boolean.parseBoolean(raw));
                case INT -> e.set(Integer.parseInt(raw));
            }
        } catch (Exception ex) {
            return "Invalid value '" + raw + "' for " + key + " (expected " + e.type + ")";
        }
        return null;
    }
}
